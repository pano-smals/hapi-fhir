package ca.uhn.fhir.jpa.searchparam.registry;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.RuntimeSearchParam;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.interceptor.api.IInterceptorService;
import ca.uhn.fhir.jpa.cache.*;
import ca.uhn.fhir.jpa.cache.config.RegisteredResourceListenerFactoryConfig;
import ca.uhn.fhir.jpa.model.entity.ResourceTable;
import ca.uhn.fhir.jpa.model.entity.StorageSettings;
import ca.uhn.fhir.jpa.searchparam.MatchUrlService;
import ca.uhn.fhir.jpa.searchparam.extractor.SearchParamExtractorService;
import ca.uhn.fhir.jpa.searchparam.matcher.InMemoryMatchResult;
import ca.uhn.fhir.jpa.searchparam.matcher.InMemoryResourceMatcher;
import ca.uhn.fhir.jpa.searchparam.matcher.IndexedSearchParamExtractor;
import ca.uhn.fhir.jpa.searchparam.matcher.SearchParamMatcher;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.rest.server.SimpleBundleProvider;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.rest.server.util.ISearchParamRegistry;
import ca.uhn.fhir.rest.server.util.ResourceSearchParams;
import ca.uhn.fhir.util.HapiExtensions;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.instance.model.api.IPrimitiveType;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.shaded.com.google.common.collect.Sets;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
public class SearchParamRegistryImplTest {
	public static final int TEST_SEARCH_PARAMS = 3;
	private static final FhirContext ourFhirContext = FhirContext.forR4();
	private static final ReadOnlySearchParamCache ourBuiltInSearchParams = ReadOnlySearchParamCache.fromFhirContext(ourFhirContext, new SearchParameterCanonicalizer(ourFhirContext));
	private static final List<ResourceTable> ourEntities;
	private static final ResourceVersionMap ourResourceVersionMap;
	private static final int ourBuiltinPatientSearchParamCount;
	private static int ourLastId;

	static {
		ourEntities = new ArrayList<>();
		for (ourLastId = 0; ourLastId < TEST_SEARCH_PARAMS; ++ourLastId) {
			ourEntities.add(createEntity(ourLastId, 1));
		}
		ourResourceVersionMap = ResourceVersionMap.fromResourceTableEntities(ourEntities);
		ourBuiltinPatientSearchParamCount = ReadOnlySearchParamCache.fromFhirContext(ourFhirContext, new SearchParameterCanonicalizer(ourFhirContext)).getSearchParamMap("Patient").size();
	}

	@Autowired
	SearchParamRegistryImpl mySearchParamRegistry;
	@Autowired
	private ResourceChangeListenerRegistryImpl myResourceChangeListenerRegistry;

	@MockBean
	private IResourceVersionSvc myResourceVersionSvc;
	@MockBean
	private ISearchParamProvider mySearchParamProvider;
	@MockBean
	private IInterceptorService myInterceptorBroadcaster;
	@MockBean
	private SearchParamMatcher mySearchParamMatcher;
	@MockBean
	private MatchUrlService myMatchUrlService;
	@MockBean
	private SearchParamExtractorService mySearchParamExtractorService;
	@MockBean
	private IndexedSearchParamExtractor myIndexedSearchParamExtractor;
	private int myAnswerCount = 0;

	@Nonnull
	private static ResourceTable createEntity(long theId, int theVersion) {
		ResourceTable searchParamEntity = new ResourceTable();
		searchParamEntity.setResourceType("SearchParameter");
		searchParamEntity.setId(theId);
		searchParamEntity.setVersionForUnitTest(theVersion);
		return searchParamEntity;
	}

	@BeforeEach
	public void before() {
		myAnswerCount = 0;
		when(myResourceVersionSvc.getVersionMap(anyString(), any())).thenReturn(ourResourceVersionMap);
		when(mySearchParamProvider.search(any())).thenReturn(new SimpleBundleProvider());

		// Our first refresh adds our test searchparams to the registry
		assertResult(mySearchParamRegistry.refreshCacheIfNecessary(), TEST_SEARCH_PARAMS, 0, 0);
		assertEquals(TEST_SEARCH_PARAMS, myResourceChangeListenerRegistry.getResourceVersionCacheSizeForUnitTest());
		assertDbCalled();
		assertEquals(ourBuiltInSearchParams.size(), mySearchParamRegistry.getActiveSearchParams().size());
		assertPatientSearchParamSize(ourBuiltinPatientSearchParamCount);
	}

	@AfterEach
	public void after() {
		myResourceChangeListenerRegistry.clearCachesForUnitTest();
		// Empty out the searchparam registry
		mySearchParamRegistry.resetForUnitTest();
	}

	@Test
	void handleInit() {
		assertEquals(31, mySearchParamRegistry.getActiveSearchParams("Patient").size());

		IdDt idBad = new IdDt("SearchParameter/bad");
		when(mySearchParamProvider.read(idBad)).thenThrow(new ResourceNotFoundException("id bad"));

		IdDt idGood = new IdDt("SearchParameter/good");
		SearchParameter goodSearchParam = buildSearchParameter(Enumerations.PublicationStatus.ACTIVE);
		when(mySearchParamProvider.read(idGood)).thenReturn(goodSearchParam);

		List<IIdType> idList = new ArrayList<>();
		idList.add(idBad);
		idList.add(idGood);
		mySearchParamRegistry.handleInit(idList);
		assertEquals(32, mySearchParamRegistry.getActiveSearchParams("Patient").size());
	}

	@Test
	public void testRefreshAfterExpiry() {
		mySearchParamRegistry.requestRefresh();
		// Second time we don't need to run because we ran recently
		assertEmptyResult(mySearchParamRegistry.refreshCacheIfNecessary());
	}

	@Test
	public void testRefreshCacheIfNecessary() {
		// Second refresh does not call the database
		assertEmptyResult(mySearchParamRegistry.refreshCacheIfNecessary());
		assertEquals(TEST_SEARCH_PARAMS, myResourceChangeListenerRegistry.getResourceVersionCacheSizeForUnitTest());
		assertDbNotCalled();
		assertPatientSearchParamSize(ourBuiltinPatientSearchParamCount);

		// Requesting a refresh calls the database and adds nothing
		mySearchParamRegistry.requestRefresh();
		assertEmptyResult(mySearchParamRegistry.refreshCacheIfNecessary());
		assertEquals(TEST_SEARCH_PARAMS, myResourceChangeListenerRegistry.getResourceVersionCacheSizeForUnitTest());
		assertDbCalled();
		assertPatientSearchParamSize(ourBuiltinPatientSearchParamCount);

		// Requesting a refresh after adding a new search parameter calls the database and adds one
		resetDatabaseToOrigSearchParamsPlusNewOneWithStatus(Enumerations.PublicationStatus.ACTIVE);
		mySearchParamRegistry.requestRefresh();
		assertResult(mySearchParamRegistry.refreshCacheIfNecessary(), 1, 0, 0);
		assertEquals(TEST_SEARCH_PARAMS + 1, myResourceChangeListenerRegistry.getResourceVersionCacheSizeForUnitTest());
		assertDbCalled();
		assertPatientSearchParamSize(ourBuiltinPatientSearchParamCount + 1);

		// Requesting a refresh after adding a new search parameter calls the database and
		// removes the one added above and adds this new one
		resetDatabaseToOrigSearchParamsPlusNewOneWithStatus(Enumerations.PublicationStatus.ACTIVE);
		mySearchParamRegistry.requestRefresh();
		assertResult(mySearchParamRegistry.refreshCacheIfNecessary(), 1, 0, 1);
		assertEquals(TEST_SEARCH_PARAMS + 1, myResourceChangeListenerRegistry.getResourceVersionCacheSizeForUnitTest());
		assertDbCalled();
		assertPatientSearchParamSize(ourBuiltinPatientSearchParamCount + 1);

		// Requesting a refresh after adding a new search parameter calls the database,
		// removes the ACTIVE one and adds the new one because this is a mock test
		resetDatabaseToOrigSearchParamsPlusNewOneWithStatus(Enumerations.PublicationStatus.DRAFT);
		mySearchParamRegistry.requestRefresh();
		assertEquals(TEST_SEARCH_PARAMS + 1, myResourceChangeListenerRegistry.getResourceVersionCacheSizeForUnitTest());
		assertResult(mySearchParamRegistry.refreshCacheIfNecessary(), 1, 0, 1);
		assertDbCalled();
		// the new one does not appear in our patient search params because it's DRAFT
		assertPatientSearchParamSize(ourBuiltinPatientSearchParamCount);
	}

	@Test
	public void testSearchParamUpdate() {
		// Requesting a refresh after adding a new search parameter calls the database and adds one
		List<ResourceTable> newEntities = resetDatabaseToOrigSearchParamsPlusNewOneWithStatus(Enumerations.PublicationStatus.ACTIVE);
		mySearchParamRegistry.requestRefresh();
		assertResult(mySearchParamRegistry.refreshCacheIfNecessary(), 1, 0, 0);
		assertEquals(TEST_SEARCH_PARAMS + 1, myResourceChangeListenerRegistry.getResourceVersionCacheSizeForUnitTest());
		assertDbCalled();
		assertPatientSearchParamSize(ourBuiltinPatientSearchParamCount + 1);

		// Update the resource without changing anything that would affect our cache
		ResourceTable lastEntity = newEntities.get(newEntities.size() - 1);
		lastEntity.setVersionForUnitTest(2);
		resetMock(Enumerations.PublicationStatus.ACTIVE, newEntities);
		mySearchParamRegistry.requestRefresh();
		assertResult(mySearchParamRegistry.refreshCacheIfNecessary(), 0, 1, 0);
		assertEquals(TEST_SEARCH_PARAMS + 1, myResourceChangeListenerRegistry.getResourceVersionCacheSizeForUnitTest());
		assertDbCalled();
		assertPatientSearchParamSize(ourBuiltinPatientSearchParamCount + 1);
	}

	private void assertPatientSearchParamSize(int theExpectedSize) {
		assertEquals(theExpectedSize, mySearchParamRegistry.getActiveSearchParams("Patient").size());
	}

	private void assertResult(ResourceChangeResult theResult, long theExpectedAdded, long theExpectedUpdated, long theExpectedRemoved) {
		assertEquals(theExpectedAdded, theResult.created, "added results");
		assertEquals(theExpectedUpdated, theResult.updated, "updated results");
		assertEquals(theExpectedRemoved, theResult.deleted, "removed results");
	}

	private void assertEmptyResult(ResourceChangeResult theResult) {
		assertResult(theResult, 0, 0, 0);
	}

	private void assertDbCalled() {
		verify(myResourceVersionSvc, times(1)).getVersionMap(anyString(), any());
		reset(myResourceVersionSvc);
		when(myResourceVersionSvc.getVersionMap(anyString(), any())).thenReturn(ourResourceVersionMap);
	}

	private void assertDbNotCalled() {
		verify(myResourceVersionSvc, never()).getVersionMap(anyString(), any());
		reset(myResourceVersionSvc);
		when(myResourceVersionSvc.getVersionMap(anyString(), any())).thenReturn(ourResourceVersionMap);
	}

	@Test
	public void testGetActiveUniqueSearchParams_Empty() {
		assertThat(mySearchParamRegistry.getActiveComboSearchParams("Patient"), is(empty()));
	}

	@Test
	public void testGetActiveSearchParamsRetries() {
		AtomicBoolean retried = new AtomicBoolean(false);
		when(myResourceVersionSvc.getVersionMap(anyString(), any())).thenAnswer(t -> {
			if (myAnswerCount == 0) {
				myAnswerCount++;
				retried.set(true);
				throw new InternalErrorException("this is an error!");
			}

			return ourResourceVersionMap;
		});

		assertFalse(retried.get());
		mySearchParamRegistry.forceRefresh();
		ResourceSearchParams activeSearchParams = mySearchParamRegistry.getActiveSearchParams("Patient");
		assertTrue(retried.get());
		assertEquals(ourBuiltInSearchParams.getSearchParamMap("Patient").size(), activeSearchParams.size());
	}

	@Test
	public void testAddActiveSearchparam() {
		// Initialize the registry
		mySearchParamRegistry.forceRefresh();

		resetDatabaseToOrigSearchParamsPlusNewOneWithStatus(Enumerations.PublicationStatus.ACTIVE);

		mySearchParamRegistry.forceRefresh();
		ResourceSearchParams activeSearchParams = mySearchParamRegistry.getActiveSearchParams("Patient");

		RuntimeSearchParam converted = activeSearchParams.get("foo");
		assertNotNull(converted);

		assertEquals(1, converted.getExtensions("http://foo").size());
		IPrimitiveType<?> value = (IPrimitiveType<?>) converted.getExtensions("http://foo").get(0).getValue();
		assertEquals("FOO", value.getValueAsString());
	}

	@Test
	public void testUpliftRefchains() {
		SearchParameter sp = new SearchParameter();
		Extension upliftRefChain = sp.addExtension().setUrl(HapiExtensions.EXTENSION_SEARCHPARAM_UPLIFT_REFCHAIN);
		upliftRefChain.addExtension(HapiExtensions.EXTENSION_SEARCHPARAM_UPLIFT_REFCHAIN_PARAM_CODE, new CodeType("name1"));
		upliftRefChain.addExtension(HapiExtensions.EXTENSION_SEARCHPARAM_UPLIFT_REFCHAIN_ELEMENT_NAME, new StringType("element1"));
		Extension upliftRefChain2 = sp.addExtension().setUrl(HapiExtensions.EXTENSION_SEARCHPARAM_UPLIFT_REFCHAIN);
		upliftRefChain2.addExtension(HapiExtensions.EXTENSION_SEARCHPARAM_UPLIFT_REFCHAIN_PARAM_CODE, new CodeType("name2"));
		sp.setCode("subject");
		sp.setName("subject");
		sp.setDescription("Modified Subject");
		sp.setStatus(Enumerations.PublicationStatus.ACTIVE);
		sp.setType(Enumerations.SearchParamType.REFERENCE);
		sp.setExpression("Encounter.subject");
		sp.addBase("Encounter");
		sp.addTarget("Patient");

		ArrayList<ResourceTable> newEntities = new ArrayList<>(ourEntities);
		newEntities.add(createEntity(99, 1));
		ResourceVersionMap newResourceVersionMap = ResourceVersionMap.fromResourceTableEntities(newEntities);
		when(myResourceVersionSvc.getVersionMap(anyString(), any())).thenReturn(newResourceVersionMap);
		when(mySearchParamProvider.search(any())).thenReturn(new SimpleBundleProvider(sp));

		mySearchParamRegistry.forceRefresh();

		RuntimeSearchParam canonicalSp = mySearchParamRegistry.getRuntimeSearchParam("Encounter", "subject");
		assertEquals("Modified Subject", canonicalSp.getDescription());
		assertTrue(canonicalSp.hasUpliftRefchain("name1"));
		assertFalse(canonicalSp.hasUpliftRefchain("name99"));
		assertEquals(Sets.newHashSet("name1", "name2"), canonicalSp.getUpliftRefchainCodes());
	}

	private List<ResourceTable> resetDatabaseToOrigSearchParamsPlusNewOneWithStatus(Enumerations.PublicationStatus theStatus) {
		// Add a new search parameter entity
		List<ResourceTable> newEntities = new ArrayList(ourEntities);
		newEntities.add(createEntity(++ourLastId, 1));
		resetMock(theStatus, newEntities);
		return newEntities;
	}

	private void resetMock(Enumerations.PublicationStatus theStatus, List<ResourceTable> theNewEntities) {
		ResourceVersionMap resourceVersionMap = ResourceVersionMap.fromResourceTableEntities(theNewEntities);
		when(myResourceVersionSvc.getVersionMap(anyString(), any())).thenReturn(resourceVersionMap);

		// When we ask for the new entity, return our foo search parameter
		when(mySearchParamProvider.search(any())).thenReturn(new SimpleBundleProvider(buildSearchParameter(theStatus)));
	}

	@Nonnull
	private SearchParameter buildSearchParameter(Enumerations.PublicationStatus theStatus) {
		SearchParameter searchParameter = new SearchParameter();
		searchParameter.setCode("foo");
		searchParameter.setStatus(theStatus);
		searchParameter.setType(Enumerations.SearchParamType.TOKEN);
		searchParameter.setExpression("Patient.name");
		searchParameter.addBase("Patient");
		searchParameter.addExtension("http://foo", new StringType("FOO"));
		searchParameter.addExtension("http://bar", new StringType("BAR"));

		// Invalid entries
		searchParameter.addExtension("http://bar", null);
		searchParameter.addExtension(null, new StringType("BAR"));
		return searchParameter;
	}

	@Configuration
	@Import(RegisteredResourceListenerFactoryConfig.class)
	static class SpringConfig {
		@Bean
		FhirContext fhirContext() {
			return ourFhirContext;
		}

		@Bean
		StorageSettings storageSettings() {
			StorageSettings storageSettings = new StorageSettings();
			storageSettings.setDefaultSearchParamsCanBeOverridden(true);
			return storageSettings;
		}

		@Bean
		ISearchParamRegistry searchParamRegistry() {
			return new SearchParamRegistryImpl();
		}

		@Bean
		SearchParameterCanonicalizer searchParameterCanonicalizer(FhirContext theFhirContext) {
			return new SearchParameterCanonicalizer(theFhirContext);
		}

		@Bean
		IResourceChangeListenerRegistry resourceChangeListenerRegistry(FhirContext theFhirContext, ResourceChangeListenerCacheFactory theResourceChangeListenerCacheFactory, InMemoryResourceMatcher theInMemoryResourceMatcher) {
			return new ResourceChangeListenerRegistryImpl(theFhirContext, theResourceChangeListenerCacheFactory, theInMemoryResourceMatcher);
		}

		@Bean
		ResourceChangeListenerCacheRefresherImpl resourceChangeListenerCacheRefresher() {
			return new ResourceChangeListenerCacheRefresherImpl();
		}

		@Bean
		InMemoryResourceMatcher inMemoryResourceMatcher() {
			InMemoryResourceMatcher retval = mock(InMemoryResourceMatcher.class);
			when(retval.canBeEvaluatedInMemory(any(), any())).thenReturn(InMemoryMatchResult.successfulMatch());
			return retval;
		}

		@Bean
		IValidationSupport validationSupport() {
			return mock(IValidationSupport.class);
		}

	}

}
