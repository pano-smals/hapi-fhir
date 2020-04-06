package ca.uhn.fhir.jpa.empi.interceptor;

import ca.uhn.fhir.jpa.dao.DaoMethodOutcome;
import ca.uhn.fhir.jpa.empi.BaseEmpiR4Test;
import ca.uhn.fhir.jpa.empi.entity.EmpiLink;
import ca.uhn.fhir.jpa.empi.util.EmpiHelperR4;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Person;
import org.hl7.fhir.r4.model.Reference;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.Assert.*;
import static org.slf4j.LoggerFactory.getLogger;

public class EmpiInterceptorTest extends BaseEmpiR4Test {

	private static final Logger ourLog = getLogger(EmpiInterceptorTest.class);

	@Rule
	@Autowired
	public EmpiHelperR4 myEmpiHelper;

	@Test
	public void testCreatePatient() throws InterruptedException {
		myEmpiHelper.createWithLatch(new Patient());
		assertLinkCount(1);
	}

	@Test
	public void testCreatePerson() throws InterruptedException {
		myPersonDao.create(new Person());
		assertLinkCount(0);
	}

	// FIXME EMPI restrict to empi-managed Person resources
	@Test
	public void testCreateUpdatePersonWithLinkForbiddenWhenEmpiEnabledAndPersonIsEmpiManaged() throws InterruptedException {
		// When EMPI is enabled, only the EMPI system is allowed to modify Person links
		Patient patient = new Patient();
		IIdType patientId = myEmpiHelper.createWithLatch(new Patient()).getId().toUnqualifiedVersionless();
		patient.setId(patientId);

		//Just a small sanity check for this custom matcher
		assertThat(patient, samePersonAs(patient));

		//With no links is fine
		Person person = new Person();
		DaoMethodOutcome daoMethodOutcome = myEmpiHelper.doCreatePerson(person, true);
		assertNotNull(daoMethodOutcome.getId());

		//Creating a person with links should fail.
		person.addLink().setTarget(new Reference("123"));
		try {
			myEmpiHelper.doCreatePerson(person, true);
			fail();
		} catch (ForbiddenOperationException e) {
			assertEquals("Cannot modify Person links when EMPI is enabled.", e.getMessage());
		}

		//Updating a person with while modifying their links should fail.
		person.setId("TESTID");
		try {
			myEmpiHelper.doCreatePerson(person, true);
			fail();
		} catch (ForbiddenOperationException e) {
			assertEquals("Cannot modify Person links when EMPI is enabled.", e.getMessage());
		}

		//Updating a person's data should work, so long as you aren't trying to modify links.
		Person savedPerson = (Person) daoMethodOutcome.getResource();
		savedPerson.setId(daoMethodOutcome.getId().toUnqualifiedVersionless());
		savedPerson.getNameFirstRep().setFamily("Graham");
		savedPerson.getLink().clear();
		DaoMethodOutcome daoMethodOutcome1 = myEmpiHelper.doUpdatePerson(savedPerson, true);
		assertNotNull(daoMethodOutcome1.getId());
	}

	@Test
	public void testUpdatingExistingLinksIsForbiddenViaPersonEndpoint() throws InterruptedException {
		myEmpiHelper.createWithLatch(new Patient());
		assertLinkCount(1);
	}

	@Test
	public void testPersonRecordsManagedByEmpiAllShareSameExtension() {
		fail();
	}
}
