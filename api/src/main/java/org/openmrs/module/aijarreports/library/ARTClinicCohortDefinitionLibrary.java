package org.openmrs.module.aijarreports.library;

import org.openmrs.module.reporting.cohort.definition.CohortDefinition;
import org.openmrs.module.reporting.definition.library.BaseDefinitionLibrary;
import org.springframework.stereotype.Component;

/**
 * Defines all the Cohort Definitions instances from the ART clinic
 */
@Component
public class ARTClinicCohortDefinitionLibrary extends BaseDefinitionLibrary<CohortDefinition> {
	
	@Override
	public Class<? super CohortDefinition> getDefinitionType() {
		return CohortDefinition.class;
	}

	@Override
	public String getKeyPrefix() {
		return "aijar.cohortdefinition.art.";
	}
}