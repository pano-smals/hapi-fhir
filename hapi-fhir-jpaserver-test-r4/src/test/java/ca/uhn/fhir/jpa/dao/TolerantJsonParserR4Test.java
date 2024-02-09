package ca.uhn.fhir.jpa.dao;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.LenientErrorHandler;
import org.hl7.fhir.r4.model.Observation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TolerantJsonParserR4Test {

	private final FhirContext myFhirContext = FhirContext.forR4Cached();

	@Test
	public void testParseInvalidNumeric_LeadingDecimal() {
		String input = "{\n" +
			"\"resourceType\": \"Observation\",\n" +
			"\"valueQuantity\": {\n" +
			"      \"value\": .5\n" +
			"   }\n" +
			"}";


		TolerantJsonParser parser = new TolerantJsonParser(myFhirContext, new LenientErrorHandler(), 123L);
		Observation obs = parser.parseResource(Observation.class, input);

		assertThat(obs.getValueQuantity().getValueElement().getValueAsString()).isEqualTo("0.5");
	}

	@Test
	public void testParseInvalidNumeric_LeadingZeros() {
		String input = "{\n" +
			"\"resourceType\": \"Observation\",\n" +
			"\"valueQuantity\": {\n" +
			"      \"value\": 00.5\n" +
			"   }\n" +
			"}";


		TolerantJsonParser parser = new TolerantJsonParser(myFhirContext, new LenientErrorHandler(), 123L);
		Observation obs = parser.parseResource(Observation.class, input);

		assertThat(obs.getValueQuantity().getValueElement().getValueAsString()).isEqualTo("0.5");
	}

	@Test
	public void testParseInvalidNumeric_DoubleZeros() {
		String input = "{\n" +
			"\"resourceType\": \"Observation\",\n" +
			"\"valueQuantity\": {\n" +
			"      \"value\": 00\n" +
			"   }\n" +
			"}";


		TolerantJsonParser parser = new TolerantJsonParser(myFhirContext, new LenientErrorHandler(), 123L);
		Observation obs = parser.parseResource(Observation.class, input);

		assertThat(obs.getValueQuantity().getValueElement().getValueAsString()).isEqualTo("0");
	}

	@Test
	public void testParseInvalidNumeric2() {
		String input = "{\n" +
			"\"resourceType\": \"Observation\",\n" +
			"\"valueQuantity\": {\n" +
			"      \"value\": .\n" +
			"   }\n" +
			"}";


		TolerantJsonParser parser = new TolerantJsonParser(myFhirContext, new LenientErrorHandler(), 123L);
		try {
			parser.parseResource(Observation.class, input);
		} catch (DataFormatException e) {
			assertThat(e.getMessage()).contains("[element=\"value\"] Invalid attribute value \".\"");
		}

	}

}
