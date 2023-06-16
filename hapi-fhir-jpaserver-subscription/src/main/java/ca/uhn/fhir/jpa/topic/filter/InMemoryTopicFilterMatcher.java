/*-
 * #%L
 * HAPI FHIR Subscription Server
 * %%
 * Copyright (C) 2014 - 2023 Smile CDR, Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package ca.uhn.fhir.jpa.topic.filter;

import ca.uhn.fhir.jpa.searchparam.matcher.InMemoryMatchResult;
import ca.uhn.fhir.jpa.searchparam.matcher.SearchParamMatcher;
import ca.uhn.fhir.jpa.subscription.model.CanonicalTopicSubscriptionFilter;
import ca.uhn.fhir.rest.api.server.SystemRequestDetails;
import org.hl7.fhir.instance.model.api.IBaseResource;

public class InMemoryTopicFilterMatcher implements ISubscriptionTopicFilterMatcher {
	private final SearchParamMatcher mySearchParamMatcher;

	public InMemoryTopicFilterMatcher(SearchParamMatcher theSearchParamMatcher) {
		mySearchParamMatcher = theSearchParamMatcher;
	}

	@Override
	public InMemoryMatchResult match(CanonicalTopicSubscriptionFilter theCanonicalTopicSubscriptionFilter, IBaseResource theResource) {
		return mySearchParamMatcher.match(theCanonicalTopicSubscriptionFilter.asCriteriaString(), theResource, new SystemRequestDetails());
	}
}
