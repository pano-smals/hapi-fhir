package ca.uhn.fhir.jpa.empi.interceptor;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.jpa.api.IEmpiInterceptor;
import ca.uhn.fhir.jpa.dao.expunge.ExpungeEverythingService;
import ca.uhn.fhir.jpa.empi.entity.EmpiLink;
import ca.uhn.fhir.jpa.interceptor.BaseResourceModifiedInterceptor;
import ca.uhn.fhir.jpa.subscription.module.ResourceModifiedMessage;
import ca.uhn.fhir.jpa.subscription.module.channel.ISubscribableChannelFactory;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import com.google.common.collect.Streams;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Person;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Interceptor
@Service
public class EmpiInterceptor extends BaseResourceModifiedInterceptor implements IEmpiInterceptor {
	private static final Logger ourLog = LoggerFactory.getLogger(EmpiInterceptor.class);

	private static final String EMPI_MATCHING_CHANNEL_NAME = "empi-matching";
	@Autowired
	private ExpungeEverythingService myExpungeEverythingService;
	@Autowired
	private ISubscribableChannelFactory mySubscribableChannelFactory;
	@Autowired
	private EmpiMatchingSubscriber myEmpiMatchingSubscriber;

	@Override
	protected String getMatchingChannelName() {
		return EMPI_MATCHING_CHANNEL_NAME;
	}

	@Override
	protected MessageHandler getSubscriber() {
		return myEmpiMatchingSubscriber;
	}

	@Override
	protected SubscribableChannel createMatchingChannel() {
		return mySubscribableChannelFactory.createSubscribableChannel(EMPI_MATCHING_CHANNEL_NAME, ResourceModifiedMessage.class, 1);
	}

	@Hook(Pointcut.STORAGE_PRESTORAGE_EXPUNGE_EVERYTHING)
	public void expungeAllEmpiLinks(AtomicInteger theCounter) {
		ourLog.debug("Expunging all EmpiLink records");
		theCounter.addAndGet(myExpungeEverythingService.expungeEverythingByType(EmpiLink.class));
	}

	@Hook(Pointcut.STORAGE_PRESTORAGE_EXPUNGE_RESOURCE)
	public void expungeAllMatchedEmpiLinks(AtomicInteger theCounter, IBaseResource theResource) {
		// FIXME EMPI
	}

	@Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_CREATED)
	public void blockManualPersonManipulationOnCreate(IBaseResource theBaseResource, RequestDetails theRequestDetails, ServletRequestDetails theServletRequestDetails) {
		if (isInternalRequest(theRequestDetails)) {
			return;
		}
		forbidCreationOfPersonsWithLinks(theBaseResource);
	}

	@Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_UPDATED)
	public void blockManualPersonManipulationOnUpdate(IBaseResource theOldResource, IBaseResource theNewResource, RequestDetails theRequestDetails, ServletRequestDetails theServletRequestDetails) {
		if (isInternalRequest(theRequestDetails)) {
			return;
		}
		forbidModificationOnLinksPerson(theOldResource, theNewResource);
	}

	/*
	 * We assume that if we have RequestDetails, then this was an HTTP request and not an internal one.
	 */
	private boolean isInternalRequest(RequestDetails theRequestDetails) {
		return theRequestDetails == null;
	}

	/**
	 * If we find that an updated Person has some changed values in their links, we reject the incoming change.
	 * @param theOldResource
	 * @param theNewResource
	 */
	private void forbidModificationOnLinksPerson(IBaseResource theOldResource, IBaseResource theNewResource) {
		boolean linksWereModified = false;
		if (extractResourceType(theNewResource).equalsIgnoreCase("Person")) {
			Person newPerson = (Person)theNewResource;
			Person oldPerson = (Person) theOldResource;
			if (newPerson.getLink().size() != oldPerson.getLink().size()) {
				linksWereModified = true;
			}
			Stream<Boolean> linkMatches = Streams.zip(newPerson.getLink().stream(), oldPerson.getLink().stream(), Person.PersonLinkComponent::equalsDeep);

			linksWereModified |= linkMatches.anyMatch(matched -> !matched);

			if (linksWereModified) {
				throwBlockedByEmpi();
			}
		}
	}

	private void forbidCreationOfPersonsWithLinks(IBaseResource theResource) {
		if (extractResourceType(theResource).equalsIgnoreCase("Person")) {
			Person p = (Person)theResource;
			if (!p.getLink().isEmpty()) {
				throwBlockedByEmpi();
			}
		}
	}

	private void throwBlockedByEmpi(){
		throw new ForbiddenOperationException("Cannot modify Person links when EMPI is enabled.");
	}

	private String extractResourceType(IBaseResource theResource) {
		return myFhirContext.getResourceDefinition(theResource).getName();
	}
}
