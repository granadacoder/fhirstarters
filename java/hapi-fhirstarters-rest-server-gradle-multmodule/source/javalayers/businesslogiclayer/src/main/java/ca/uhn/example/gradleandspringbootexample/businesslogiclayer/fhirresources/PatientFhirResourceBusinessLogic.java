package ca.uhn.example.gradleandspringbootexample.businesslogiclayer.fhirresources;

import ca.uhn.example.gradleandspringbootexample.businesslogiclayer.fhirresources.interfaces.IPatientFhirResourceBusinessLogic;
import ca.uhn.fhir.model.primitive.InstantDt;
import ca.uhn.fhir.model.primitive.StringDt;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.param.DateParam;
import ca.uhn.fhir.rest.param.ParamPrefixEnum;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import org.apache.commons.lang3.time.DateUtils;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class PatientFhirResourceBusinessLogic implements IPatientFhirResourceBusinessLogic {

   public static final int DEMO_PATIENT_FHIR_LOGICAL_ID_START = 51;

   public static final int PATIENT_SEED_COUNT = 10;

   private static final int GIVEN_MODULUS = 4;

   private static final int GIVEN_MODULUS_MATCH_ZERO = 0;

   private static final int GIVEN_MODULUS_MATCH_ONE = 1;

   private static final int GIVEN_MODULUS_MATCH_TWO = 2;

   private static final int GIVEN_MODULUS_MATCH_THREE = 3;

   private static final int GENDER_MODULUS = 2;

   private static final int FAMILY_MODULUS = 3;

   private static final LocalDate PATIENT_BIRTH_DATE_STATIC_REFERENCE_JULY_28_2000 = LocalDate.of(2000, 7, 28);

   private static final Logger LOGGER = LoggerFactory.getLogger(PatientFhirResourceBusinessLogic.class);

   /**
    * This map has a resource ID as a key, and each key maps to a Deque list containing all versions of the resource with that ID.
    */
   private Map<Long, Deque<Patient>> myIdToPatientVersions = new HashMap<Long, Deque<Patient>>();

   /**
    * This is used to generate new IDs.
    */
   private long myNextId = DEMO_PATIENT_FHIR_LOGICAL_ID_START;

   @Inject
   public PatientFhirResourceBusinessLogic() {
      this.setupFakeResourceProvider();
   }

   private void setupFakeResourceProvider() {

      /* add i number of seed data patients */
      for (int i = DEMO_PATIENT_FHIR_LOGICAL_ID_START; i < PATIENT_SEED_COUNT + DEMO_PATIENT_FHIR_LOGICAL_ID_START; i++) {

         long resourceId = myNextId++;

         Patient patient = new Patient();
         patient.setId(Long.toString(resourceId));
         Identifier id1 = patient.addIdentifier();
         id1.setSystem("http://gyms.are.us.com/gyms.are.us.memberid");
         id1.setValue("GYMS.R.US..." + Long.toString(resourceId));

         Identifier id2 = patient.addIdentifier();
         id2.setSystem("http://libraries.are.cool.com/libraries.are.cool.memberid");
         id2.setValue("LIB.R.COOL..." + Long.toString(resourceId));

         HumanName hn1 = new HumanName();

         if ((resourceId % FAMILY_MODULUS) == 0) {
            hn1.setFamily("Patel");
         } else if ((resourceId % FAMILY_MODULUS) == 1) {
            hn1.setFamily("Jones");
         } else {
            hn1.setFamily("Smith");
         }

         hn1.addGiven("MyFirstName" + Long.toString(resourceId));
         hn1.addGiven("MyMiddleOneName" + Long.toString(resourceId));
         hn1.addGiven("MyMiddleTwoName" + Long.toString(resourceId));

         patient.addName(hn1);

         if ((resourceId % GENDER_MODULUS) == 0) {
            patient.setGender(AdministrativeGender.FEMALE);

            if ((resourceId % GIVEN_MODULUS) == GIVEN_MODULUS_MATCH_ZERO) {
               hn1.addGiven("Fatima");
            } else if ((resourceId % GIVEN_MODULUS) == GIVEN_MODULUS_MATCH_TWO) {
               hn1.addGiven("Mary");
            } else {
               hn1.setFamily("Pat");
            }

         } else {
            patient.setGender(AdministrativeGender.MALE);

            if ((resourceId % GIVEN_MODULUS) == GIVEN_MODULUS_MATCH_ONE) {
               hn1.addGiven("John");
            } else if ((resourceId % GIVEN_MODULUS) == GIVEN_MODULUS_MATCH_THREE) {
               hn1.addGiven("Sri");
            } else {
               hn1.setFamily("Pat");
            }

         }

         /* use "minusMonths" to give variety to the birthdates */
         LocalDate localComputedBirthDate = PATIENT_BIRTH_DATE_STATIC_REFERENCE_JULY_28_2000.plusMonths(resourceId);
         Date birthDate = Date.from(localComputedBirthDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
         patient.setBirthDate(birthDate);

         /* REFERENCE to a managing-organization.  You should NOT under-estimate the issue of "references" in FHIR */
         /* https://www.hl7.org/fhir/patient-definitions.html#Patient.managingOrganization*/
         /* Organization that is the custodian of the patient record */
         /* here we grab our single example organization */
         patient.setManagingOrganization(new Reference(String.format("/Organization/%1$s", OrganizationFhirResourceBusinessLogic.DEMO_ORGANIZATION_ONE_FHIR_LOGICAL_ID)));

         LinkedList<Patient> list = new LinkedList<Patient>();
         list.add(patient);

         myIdToPatientVersions.put(resourceId, list);

      }
   }

   /**
    * Stores a new version of the patient in memory so that it can be retrieved later.
    *
    * @param thePatient The patient resource to store
    * @param theId      The ID of the patient to retrieve
    */
   private void addNewVersion(final Patient thePatient, final Long theId) {
      InstantDt publishedDate;
      if (!myIdToPatientVersions.containsKey(theId)) {
         myIdToPatientVersions.put(theId, new LinkedList<Patient>());
         publishedDate = InstantDt.withCurrentTime();
      } else {
         Patient currentPatitne = myIdToPatientVersions.get(theId).getLast();
         Meta resourceMetadata = currentPatitne.getMeta();
         publishedDate = InstantDt.withCurrentTime();  //(InstantDt) resourceMetadata.get(ResourceMetadataKeyEnum.PUBLISHED);
      }

      /*
       * PUBLISHED time will always be set to the time that the first version was stored. UPDATED time is set to the time that the new version was stored.
       */
      //thePatient.getMeta().put(ResourceMetadataKeyEnum.PUBLISHED, publishedDate);
      thePatient.getMeta().setLastUpdated(InstantDt.withCurrentTime().getValue());

      Deque<Patient> existingVersions = myIdToPatientVersions.get(theId);

      // We just use the current number of versions as the next version number
      String newVersion = Integer.toString(existingVersions.size());

      // Create an ID with the new version and assign it back to the resource
      IdType newId = new IdType("Patient", Long.toString(theId), newVersion);
      thePatient.setId(newId);

      existingVersions.add(thePatient);
   }

   public IdType createPatient(@ResourceParam Patient thePatient) {
      validateResource(thePatient);

      // Here we are just generating IDs sequentially
      long id = myNextId++;

      addNewVersion(thePatient, id);

      // Let the caller know the ID of the newly created resource
      return new IdType(id);
   }

   public List<Patient> findBySearchParameters(
      StringDt theFamilyName,
      StringDt theGivenName,
      DateParam theBirthDate) {

      List<Patient> lastEntriesInDequeuePatients = myIdToPatientVersions
         .values()
         .stream()
         .map(dq -> dq.getLast())
         .collect(Collectors.toList());

      Stream<Patient> queryableStream = lastEntriesInDequeuePatients.stream();

      /* build up the queryableStream based on the optional parameters */

      /* check for optional family-name and add to queryableStream */
      if (null != theFamilyName) {
         queryableStream = queryableStream.filter(pat ->
            null != pat.getName() && pat.getName().stream().anyMatch(hn -> null != hn.getFamily() && hn.getFamily().equalsIgnoreCase(theFamilyName.getValue()))
         );
      }

      /* check for optional given-name and add to queryableStream */
      if (null != theGivenName) {
         queryableStream = queryableStream.filter(pat ->
            null != pat.getName() && pat.getName().stream().anyMatch(hn -> null != hn.getGiven() && hn.getGiven().stream().anyMatch(gv -> gv.getValue().equalsIgnoreCase(theGivenName.getValue())))
         );
      }

      /* check for optional birthday and add to queryableStream : WITH CONSIDERATION TO MODIFIERS */
      if (theBirthDate != null && null != theBirthDate.getValue()) {

         /* OFTEN OVERLOOKED is that the fhir-filter-parameters have MODIFIERS on them.  Please see : https://build.fhir.org/search.html#prefix */

         /* if no modifier prefix is given, assume equal */
         if (null == theBirthDate.getPrefix() || theBirthDate.getPrefix() == ParamPrefixEnum.EQUAL) {
            queryableStream = queryableStream.filter(pat -> null != pat.getBirthDate() && DateUtils.isSameDay(pat.getBirthDate(), theBirthDate.getValue()));
         }

         if (theBirthDate.getPrefix() == ParamPrefixEnum.NOT_EQUAL) {
            queryableStream = queryableStream.filter(pat -> null != pat.getBirthDate() && !DateUtils.isSameDay(pat.getBirthDate(), theBirthDate.getValue()));
         }

         if (theBirthDate.getPrefix() == ParamPrefixEnum.GREATERTHAN_OR_EQUALS) {
            queryableStream = queryableStream.filter(pat -> null != pat.getBirthDate() && (pat.getBirthDate().after(theBirthDate.getValue()) || DateUtils.isSameDay(pat.getBirthDate(), theBirthDate.getValue())));
         }

         if (theBirthDate.getPrefix() == ParamPrefixEnum.GREATERTHAN) {
            queryableStream = queryableStream.filter(pat -> null != pat.getBirthDate() && (pat.getBirthDate().after(theBirthDate.getValue()) && !DateUtils.isSameDay(pat.getBirthDate(), theBirthDate.getValue())));
         }

         if (theBirthDate.getPrefix() == ParamPrefixEnum.LESSTHAN_OR_EQUALS) {
            queryableStream = queryableStream.filter(pat -> null != pat.getBirthDate() && (pat.getBirthDate().before(theBirthDate.getValue()) || DateUtils.isSameDay(pat.getBirthDate(), theBirthDate.getValue())));
         }

         if (theBirthDate.getPrefix() == ParamPrefixEnum.LESSTHAN) {
            queryableStream = queryableStream.filter(pat -> null != pat.getBirthDate() && (pat.getBirthDate().before(theBirthDate.getValue()) && !DateUtils.isSameDay(pat.getBirthDate(), theBirthDate.getValue())));
         }

         /* NOT YET CODED

            sa	the value for the parameter in the resource starts after the provided value
               the range of the search value does not overlap with the range of the target value, and the range above the search value contains the range of the target value

            eb	the value for the parameter in the resource ends before the provided value
               the range of the search value does not overlap with the range of the target value, and the range below the search value contains the range of the target value

            ap	the value for the parameter in the resource is approximately the same to the provided value.
                 Note that the recommended value for the approximation is 10% of the stated value (or for a date, 10% of the gap between now and the date), but systems may choose other values where appropriate

          */
      }

      List<Patient> matchPatients = queryableStream.collect(Collectors.toList());

      return matchPatients;
   }

   public Optional<Patient> readPatient(@IdParam IdType theId) {

      Optional<Patient> returnItem = Optional.empty();

      Deque<Patient> retVal;
      try {
         retVal = myIdToPatientVersions.get(theId.getIdPartAsLong());

         if (!theId.hasVersionIdPart()) {
            returnItem = Optional.of(retVal.getLast());
         } else {
            for (Patient nextVersion : retVal) {
               String nextVersionId = nextVersion.getId();
               if (theId.getVersionIdPart().equals(nextVersionId)) {
                  returnItem = Optional.of(nextVersion);
               }
            }
            // No matching version
            throw new ResourceNotFoundException("Unknown version: " + theId.getValue());
         }

      } catch (Exception e) {
         /*
          * If we can't parse the ID as a long, it's not valid so this is an unknown resource
          */

         LOGGER.error("Prove injected logger works. " + e.getMessage(), e);
         throw new ResourceNotFoundException(theId.toString());
      }

      return returnItem;

   }

   public void updatePatient(@IdParam IdType theId, @ResourceParam Patient thePatient) {
      validateResource(thePatient);

      Long id;
      try {
         id = theId.getIdPartAsLong();
      } catch (DataFormatException e) {
         throw new InvalidRequestException("Invalid ID " + theId.getValue() + " - Must be numeric");
      }

      /*
       * Throw an exception (HTTP 404) if the ID is not known
       */
      if (!myIdToPatientVersions.containsKey(id)) {
         throw new ResourceNotFoundException(theId);
      }

      addNewVersion(thePatient, id);
   }

   /**
    * This method just provides simple business validation for resources we are storing.
    *
    * @param thePatient The patient to validate
    */
   private void validateResource(final Patient thePatient) {
      /*
       * Our server will have a rule that patients must have a family name or we will reject them
       */
      if (thePatient.getNameFirstRep().isEmpty()) {
         OperationOutcome outcome = new OperationOutcome();
         CodeableConcept cc = new CodeableConcept();
         cc.setText("No family name provided, Patient resources must have at least one family name.");
         outcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.FATAL).setDetails(cc);
         throw new UnprocessableEntityException("Something bad", outcome);
      }
   }
}
