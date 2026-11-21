package io.github.rkaushikdev.staplewright.control;

import static io.github.rkaushikdev.staplewright.support.People.TODAY;
import static io.github.rkaushikdev.staplewright.support.People.aDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import java.time.LocalDate;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class RequestsTest {

    private static KeyRequest aRequest() {
        return aRequest("R-001");
    }

    private static KeyRequest aRequest(String number) {
        return new KeyRequest(number, "kmaki", "AA1", "MOO", "moved into 214", TODAY);
    }

    private static KeyRequest aRequest(UnaryOperator<KeyRequest.Builder> overrides) {
        return aRequest("R-001", overrides);
    }

    private static KeyRequest aRequest(String number, UnaryOperator<KeyRequest.Builder> overrides) {
        return overrides.apply(aRequest(number).toBuilder()).build();
    }

    private static RequestBook aBook() {
        return new RequestBook(aDirectory());
    }

    private static KeyRequest submitted(RequestBook book, KeyRequest request) {
        book.raiseRequest(request);
        return book.submit(request.number());
    }

    private static List<String> numbers(Iterable<KeyRequest> requests) {
        return StreamSupport.stream(requests.spliterator(), false)
                .map(KeyRequest::number)
                .toList();
    }

    @Test
    void aRequestNeedsANumberAndAReason() {
        assertThatThrownBy(() -> aRequest("  ")).isInstanceOf(InputError.class);
        assertThatThrownBy(() -> aRequest(request -> request.reason("  "))).isInstanceOf(InputError.class);
    }

    @Test
    void aNewRequestStartsRaisedAndOpen() {
        RequestBook book = aBook();
        KeyRequest request = book.raiseRequest(aRequest());
        assertThat(request.state()).isSameAs(RequestState.RAISED);
        assertThat(request.isOpen()).isTrue();
        assertThat(request.isDecided()).isFalse();
    }

    @Test
    void aRequestDescribesItselfForTheCounter() {
        assertThat(aRequest().describe()).isEqualTo("R-001 kmaki wants AA1 in MOO");
    }

    @Test
    void aRequestForSomebodyWhoIsNotInTheDirectory() {
        RequestBook book = aBook();
        assertThatThrownBy(() -> book.raiseRequest(aRequest(request -> request.keyholder("ghost"))))
                .isInstanceOf(NotFoundError.class);
    }

    @Test
    void aRequestNumberCannotBeReused() {
        RequestBook book = aBook();
        book.raiseRequest(aRequest());
        assertThatThrownBy(() -> book.raiseRequest(aRequest())).isInstanceOf(ConflictError.class);
    }

    @Test
    void aRequestCannotBeFiledAlreadyApproved() {
        RequestBook book = aBook();
        assertThatThrownBy(() -> book.raiseRequest(aRequest(request -> request.state(RequestState.APPROVED))))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void submittingMovesItAlong() {
        RequestBook book = aBook();
        book.raiseRequest(aRequest());
        assertThat(book.submit("R-001").state()).isSameAs(RequestState.SUBMITTED);
    }

    @Test
    void submittingTwiceIsRefused() {
        RequestBook book = aBook();
        submitted(book, aRequest());
        assertThatThrownBy(() -> book.submit("R-001")).isInstanceOf(ConflictError.class);
    }

    @Test
    void aSignatureThatReachesApprovesIt() {
        RequestBook book = aBook();
        submitted(book, aRequest());
        KeyRequest approved = book.approve("R-001", "rvirta", TODAY);
        assertThat(approved.state()).isSameAs(RequestState.APPROVED);
        assertThat(approved.decidedBy()).isEqualTo("rvirta");
        assertThat(approved.decidedOn()).contains(TODAY);
    }

    @Test
    void aSignatureFromTheWrongDepartmentDoesNotReach() {
        RequestBook book = aBook();
        submitted(book, aRequest());
        ConflictError caught = catchThrowableOfType(ConflictError.class, () -> book.approve("R-001", "phamm", TODAY));
        assertThat(caught.facts()).containsEntry("department", "geography");
    }

    @Test
    void aSignatureForTheWrongBuildingDoesNotReach() {
        RequestBook book = aBook();
        submitted(book, aRequest(request -> request.building("VOS")));
        assertThatThrownBy(() -> book.approve("R-001", "rvirta", TODAY)).isInstanceOf(ConflictError.class);
    }

    @Test
    void somebodyWhoDoesNotSignCannotApprove() {
        RequestBook book = aBook();
        submitted(book, aRequest());
        assertThatThrownBy(() -> book.approve("R-001", "dsalo", TODAY)).isInstanceOf(ConflictError.class);
    }

    @Test
    void aMasterNeedsASignatureAllowedToApproveMasters() {
        RequestBook book = aBook();
        submitted(book, aRequest(request -> request.symbol("AA").isMaster(true)));
        ConflictError caught = catchThrowableOfType(ConflictError.class, () -> book.approve("R-001", "rvirta", TODAY));
        assertThat(caught.facts()).containsEntry("symbol", "AA");
    }

    @Test
    void theRightSignatureCanApproveAMaster() {
        RequestBook book = aBook();
        submitted(
                book,
                aRequest(request ->
                        request.keyholder("phamm").building("HEI").symbol("B").isMaster(true)));
        assertThat(book.approve("R-001", "phamm", TODAY).state()).isSameAs(RequestState.APPROVED);
    }

    @Test
    void aKeyholderWhoMayNotHoldKeysCannotBeApproved() {
        Directory directory = aDirectory();
        RequestBook book = new RequestBook(directory);
        submitted(book, aRequest());
        directory.separate("kmaki", TODAY);
        ConflictError caught = catchThrowableOfType(ConflictError.class, () -> book.approve("R-001", "rvirta", TODAY));
        assertThat(caught.facts()).containsEntry("standing", "separated");
    }

    @Test
    void aLapsedContractorCannotBeApproved() {
        RequestBook book = aBook();
        submitted(book, aRequest(request -> request.keyholder("jwexler").building("HEI")));
        assertThatThrownBy(() -> book.approve("R-001", "phamm", TODAY)).isInstanceOf(ConflictError.class);
    }

    @Test
    void aDenialSaysWhy() {
        RequestBook book = aBook();
        submitted(book, aRequest());
        KeyRequest denied = book.deny("R-001", "rvirta", TODAY, "the room is shared, use the master");
        assertThat(denied.state()).isSameAs(RequestState.DENIED);
        assertThat(denied.note()).startsWith("the room is shared");
    }

    @Test
    void aDenialWithoutAReasonIsRefused() {
        RequestBook book = aBook();
        submitted(book, aRequest());
        assertThatThrownBy(() -> book.deny("R-001", "rvirta", TODAY, "  ")).isInstanceOf(InputError.class);
    }

    @Test
    void aDecidedRequestCannotBeDecidedAgain() {
        RequestBook book = aBook();
        submitted(book, aRequest());
        book.approve("R-001", "rvirta", TODAY);
        assertThatThrownBy(() -> book.deny("R-001", "rvirta", TODAY, "changed my mind"))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aRaisedRequestCannotBeApprovedBeforeItIsSubmitted() {
        RequestBook book = aBook();
        book.raiseRequest(aRequest());
        assertThatThrownBy(() -> book.approve("R-001", "rvirta", TODAY)).isInstanceOf(ConflictError.class);
    }

    @Test
    void withdrawingIsNotADenial() {
        RequestBook book = aBook();
        submitted(book, aRequest());
        KeyRequest withdrawn = book.withdraw("R-001", TODAY, "she took a different office");
        assertThat(withdrawn.state()).isSameAs(RequestState.WITHDRAWN);
        assertThat(withdrawn.isDecided()).isFalse();
    }

    @Test
    void aClosedRequestCannotBeWithdrawn() {
        RequestBook book = aBook();
        submitted(book, aRequest());
        book.approve("R-001", "rvirta", TODAY);
        assertThatThrownBy(() -> book.withdraw("R-001", TODAY)).isInstanceOf(ConflictError.class);
    }

    @Test
    void onlyAnApprovedRequestGetsFilled() {
        RequestBook book = aBook();
        submitted(book, aRequest());
        assertThatThrownBy(() -> book.markFilled("R-001")).isInstanceOf(ConflictError.class);
        book.approve("R-001", "rvirta", TODAY);
        assertThat(book.markFilled("R-001").state()).isSameAs(RequestState.FILLED);
    }

    @Test
    void requestsComeBackInTheOrderTheyWereRaised() {
        RequestBook book = aBook();
        for (String number : List.of("R-003", "R-001", "R-002")) {
            book.raiseRequest(aRequest(number));
        }
        assertThat(numbers(book)).containsExactly("R-003", "R-001", "R-002");
    }

    @Test
    void requestsCanBePulledOutByState() {
        RequestBook book = aBook();
        submitted(book, aRequest("R-001"));
        book.raiseRequest(aRequest("R-002"));
        assertThat(numbers(book.inState(RequestState.SUBMITTED))).containsExactly("R-001");
        assertThat(numbers(book.openRequests())).containsExactly("R-001", "R-002");
    }

    @Test
    void aKeyholdersOwnRequestsComeBackTogether() {
        RequestBook book = aBook();
        book.raiseRequest(aRequest("R-001"));
        book.raiseRequest(aRequest("R-002", request -> request.keyholder("dsalo")));
        assertThat(numbers(book.forKeyholder("kmaki"))).containsExactly("R-001");
    }

    @Test
    void askingAboutAKeyholderWhoIsNotThere() {
        assertThatThrownBy(() -> aBook().forKeyholder("ghost")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void whatASignerHasDecidedComesBackTogether() {
        RequestBook book = aBook();
        submitted(book, aRequest("R-001"));
        book.approve("R-001", "rvirta", TODAY);
        assertThat(numbers(book.signedBy("rvirta"))).containsExactly("R-001");
    }

    @Test
    void aDepartmentCanSeeWhatItOwesADecisionOn() {
        RequestBook book = aBook();
        submitted(book, aRequest("R-001"));
        submitted(book, aRequest("R-002", request -> request.keyholder("phamm").building("HEI")));
        assertThat(numbers(book.waitingOn("geography"))).containsExactly("R-001");
        assertThat(numbers(book.waitingOn("facilities"))).containsExactly("R-002");
    }

    @Test
    void aDecidedRequestIsNoLongerWaiting() {
        RequestBook book = aBook();
        submitted(book, aRequest("R-001"));
        book.approve("R-001", "rvirta", LocalDate.of(2025, 3, 5));
        assertThat(book.waitingOn("geography")).isEmpty();
    }

    @Test
    void theBookIsAContainer() {
        RequestBook book = aBook();
        book.raiseRequest(aRequest());
        assertThat(book.contains("R-001")).isTrue();
        assertThat(book.contains("R-999")).isFalse();
        assertThat(book.size()).isEqualTo(1);
    }

    @Test
    void askingForARequestThatIsNotThere() {
        assertThatThrownBy(() -> aBook().request("R-999")).isInstanceOf(NotFoundError.class);
    }
}
