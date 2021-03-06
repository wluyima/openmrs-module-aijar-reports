package org.openmrs.module.aijarreports.definition.data.evaluator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.LocalDate;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.annotation.Handler;
import org.openmrs.module.aijarreports.common.Period;
import org.openmrs.module.aijarreports.common.Periods;
import org.openmrs.module.aijarreports.common.StubDate;
import org.openmrs.module.aijarreports.definition.data.definition.ObsForPersonInPeriodDataDefinition;
import org.openmrs.module.aijarreports.metadata.HIVMetadata;
import org.openmrs.module.reporting.common.DateUtil;
import org.openmrs.module.reporting.common.ListMap;
import org.openmrs.module.reporting.common.TimeQualifier;
import org.openmrs.module.reporting.data.patient.EvaluatedPatientData;
import org.openmrs.module.reporting.data.patient.definition.PatientDataDefinition;
import org.openmrs.module.reporting.data.patient.evaluator.PatientDataEvaluator;
import org.openmrs.module.reporting.evaluation.EvaluationContext;
import org.openmrs.module.reporting.evaluation.EvaluationException;
import org.openmrs.module.reporting.evaluation.querybuilder.HqlQueryBuilder;
import org.openmrs.module.reporting.evaluation.service.EvaluationService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

/**
 * Created by carapai on 13/05/2016.
 */
@Handler(supports = ObsForPersonInPeriodDataDefinition.class, order = 50)
public class ObsForPersonInPeriodDataDefinitionEvaluator implements PatientDataEvaluator {
    protected static final Log log = LogFactory.getLog(ObsForPersonInPeriodDataDefinitionEvaluator.class);

    @Autowired
    private EvaluationService evaluationService;

    @Autowired
    private HIVMetadata hivMetadata;

    @Override
    public EvaluatedPatientData evaluate(PatientDataDefinition definition, EvaluationContext context) throws EvaluationException {
        ObsForPersonInPeriodDataDefinition def = (ObsForPersonInPeriodDataDefinition) definition;

        EvaluatedPatientData c = new EvaluatedPatientData(def, context);

        if (context.getBaseCohort() != null && context.getBaseCohort().isEmpty()) {
            return c;
        }

        Map<Integer, Date> m = new HashMap<Integer, Date>();

        Period period = def.getPeriod();

        Date anotherDate = def.getStartDate();

        LocalDate localEncounterStartDate = null;
        LocalDate localEncounterEndDate = null;


        HqlQueryBuilder encounterQuery = new HqlQueryBuilder();


        LocalDate workingDate = StubDate.dateOf(DateUtil.formatDate(anotherDate, "yyyy-MM-dd"));


        if (def.getWhichEncounter() != null && def.getWhichEncounter() == TimeQualifier.FIRST) {
            encounterQuery.select(new String[]{"e.encounterId", "e.patient.patientId", "MIN(e.encounterDatetime)"});
        } else if (def.getWhichEncounter() != null && def.getWhichEncounter() == TimeQualifier.LAST) {
            encounterQuery.select(new String[]{"e.encounterId", "e.patient.patientId", "MAX(e.encounterDatetime)"});
        } else {
            encounterQuery.select(new String[]{"e.encounterId", "e.patient.patientId", "e.encounterDatetime"});
        }

        encounterQuery.from(Encounter.class, "e");

        if (def.getPeriodToAdd() > 0) {
            if (period == Period.QUARTERLY) {
                List<LocalDate> dates = Periods.addQuarters(workingDate, def.getPeriodToAdd());
                localEncounterStartDate = dates.get(0);
                localEncounterEndDate = dates.get(1);
            } else if (period == Period.MONTHLY) {
                List<LocalDate> dates = Periods.addMonths(workingDate, def.getPeriodToAdd());
                localEncounterStartDate = dates.get(0);
                localEncounterEndDate = dates.get(1);
            } else {
                localEncounterStartDate = workingDate;
                localEncounterEndDate = StubDate.dateOf(DateUtil.formatDate(new Date(), "yyyy-MM-dd"));
            }

        } else {
            if (period == Period.QUARTERLY) {
                localEncounterStartDate = Periods.quarterStartFor(workingDate);
                localEncounterEndDate = Periods.quarterEndFor(workingDate);
            } else if (period == Period.MONTHLY) {
                localEncounterStartDate = Periods.monthStartFor(workingDate);
                localEncounterEndDate = Periods.monthEndFor(workingDate);
            } else {
                localEncounterStartDate = workingDate;
                localEncounterEndDate = StubDate.dateOf(DateUtil.formatDate(new Date(), "yyyy-MM-dd"));
            }
        }

        if (period != null) {
            encounterQuery.groupBy("e.patient.patientId");
        }

        encounterQuery.whereBetweenInclusive("e.encounterDatetime", localEncounterStartDate.toDate(), localEncounterEndDate.toDate());

        Set<Integer> encounters = getEncounterIds(encounterQuery, context);


        HqlQueryBuilder artStartQuery = new HqlQueryBuilder();
        artStartQuery.select("o.personId", "MIN(o.valueDatetime)");
        artStartQuery.from(Obs.class, "o");
        artStartQuery.wherePersonIn("o.personId", context);
        artStartQuery.whereIn("o.concept", hivMetadata.getConceptList("99161"));
        artStartQuery.groupBy("o.personId");


        HqlQueryBuilder q = new HqlQueryBuilder();
        q.select("o.personId", "o");
        q.from(Obs.class, "o");
        q.wherePersonIn("o.personId", context);

        if (def.getQuestion() != null) {
            q.whereEqual("o.concept", def.getQuestion());
        }

        q.whereIdIn("o.encounter", encounters);


        if (def.getAnswers() != null) {
            q.whereIn("o.valueCoded", def.getAnswers());
        }

        q.whereBetweenInclusive("o.obsDatetime", localEncounterStartDate.toDate(), localEncounterEndDate.toDate());

        q.groupBy("o.personId");


        List<Object[]> queryResult = evaluationService.evaluateToList(q, context);

        ListMap<Integer, Obs> obsForPatients = new ListMap<Integer, Obs>();

        if (period == Period.QUARTERLY) {
            m = getPatientDateMap(artStartQuery, context);
        }

        for (Object[] row : queryResult) {
            obsForPatients.putInList((Integer) row[0], (Obs) row[1]);
        }

        for (Integer pId : obsForPatients.keySet()) {
            List<Obs> l = obsForPatients.get(pId);
            Obs obs = l.get(0);

            if (period == Period.QUARTERLY) {
                /*if (m.containsKey(pId)) {
                    Date date = m.get(pId);
                    Date date2 = obs.getObsDatetime();

                    if (date2.before(date)) {
                        c.addData(pId, obs);
                    }
                }*/
                c.addData(pId, obs);
            } else {
                c.addData(pId, obs);
            }
        }

        return c;
    }

    protected Map<Integer, Date> getPatientDateMap(HqlQueryBuilder query, EvaluationContext context) {
        Map<Integer, Date> m = new HashMap<Integer, Date>();
        List<Object[]> queryResults = evaluationService.evaluateToList(query, context);
        for (Object[] row : queryResults) {
            m.put((Integer) row[0], (Date) row[1]);
        }
        return m;
    }

    protected Set<Integer> getEncounterIds(HqlQueryBuilder query, EvaluationContext context) {
        Set<Integer> m = new HashSet<Integer>();
        List<Object[]> queryResults = evaluationService.evaluateToList(query, context);
        for (Object[] row : queryResults) {
            m.add((Integer) row[0]);
        }
        return m;
    }
}
