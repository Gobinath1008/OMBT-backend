package com.omtb.backend.controllers;

import com.omtb.backend.models.Booking;
import com.omtb.backend.models.FoodOrderItem;
import com.omtb.backend.repositories.BookingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class BookingControllerTest {
    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private BookingController bookingController;

    @Test
    void getUserBookingsRequestsNewestBookingsFirst() {
        List<Booking> bookings = List.of(
                booking("newest", List.of("A1"), 200.0),
                booking("older", List.of("A2"), 200.0));
        when(bookingRepository.findByUserIdOrderByBookedAtDescIdDesc("user-1")).thenReturn(bookings);

        assertEquals(bookings, bookingController.getUserBookings("user-1"));
        verify(bookingRepository).findByUserIdOrderByBookedAtDescIdDesc("user-1");
    }

    @Test
    void createBookingSetsBookingTimestamp() {
        Booking booking = booking("booking-1", List.of("A1"), 200.0);
        when(bookingRepository.findByMovieIdAndTheaterAndDateAndTime(
                "movie-1", "INOX", "2026-10-10", "06:31 PM - 09:31 PM"))
                .thenReturn(List.of());
        when(bookingRepository.save(booking)).thenReturn(booking);

        ResponseEntity<?> response = bookingController.createBooking(booking);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(booking.getBookedAt() != null && !booking.getBookedAt().isBlank());
        verify(bookingRepository).save(eq(booking));
    }

    @Test
    void createBookingRejectsSeatsAlreadyHeldByAnotherBooking() {
        Booking existing = booking("existing", List.of("B4", "B5"), 400.0);
        Booking requested = booking("new", List.of("B4"), 200.0);
        when(bookingRepository.findByMovieIdAndTheaterAndDateAndTime(
                "movie-1", "INOX", "2026-10-10", "06:31 PM - 09:31 PM"))
                .thenReturn(List.of(existing));

        ResponseEntity<?> response = bookingController.createBooking(requested);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        verify(bookingRepository, never()).save(requested);
    }

    @Test
    void getBookedSeatsReturnsChangedSeatsAndDoesNotReturnReleasedSeats() {
        Booking updated = booking("booking-1", List.of("B4", "B5"), 400.0);
        when(bookingRepository.findByMovieIdAndTheaterAndDateAndTime(
                "movie-1", "INOX", "2026-10-10", "06:31 PM - 09:31 PM"))
                .thenReturn(List.of(updated));

        ResponseEntity<List<String>> response = bookingController.getBookedSeats(
                "movie-1", "INOX", "2026-10-10", "06:31 PM - 09:31 PM");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of("B4", "B5"), response.getBody());
        assertTrue(response.getHeaders().getCacheControl().contains("no-store"));
    }

    @Test
    void modifyBookingCancelsAndChangesSeatsAndAdjustsTotal() {
        Booking booking = booking("booking-1", List.of("A3", "A4", "A5", "A6"), 1000.0);
        when(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking));
        when(bookingRepository.findByMovieIdAndTheaterAndDateAndTime(
                "movie-1", "INOX", "2026-10-10", "06:31 PM - 09:31 PM"))
                .thenReturn(List.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);

        ResponseEntity<?> response = bookingController.modifyBooking(
                "booking-1",
                new BookingController.ModifyBookingRequest(
                        List.of("A4", "A6"),
                        List.of(
                                new BookingController.SeatChange("A3", "B3"),
                                new BookingController.SeatChange("A5", "B4"))));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        BookingController.ModifyBookingResponse body =
                assertInstanceOf(BookingController.ModifyBookingResponse.class, response.getBody());
        assertFalse(body.bookingCancelled());
        assertEquals(500.0, body.refundAmount());
        assertEquals(0.0, body.priceDifference());
        assertEquals(List.of("B3", "B4"), body.booking().getSeats());
        assertEquals(500.0, body.booking().getTotal());
    }

    @Test
    void modifyBookingDeletesBookingWhenEverySeatIsCancelled() {
        Booking booking = booking("booking-1", List.of("C1", "C2"), 400.0);
        when(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking));

        ResponseEntity<?> response = bookingController.modifyBooking(
                "booking-1",
                new BookingController.ModifyBookingRequest(List.of("C1", "C2"), List.of()));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        BookingController.ModifyBookingResponse body =
                assertInstanceOf(BookingController.ModifyBookingResponse.class, response.getBody());
        assertTrue(body.bookingCancelled());
        assertNull(body.booking());
        assertEquals(400.0, body.refundAmount());
        verify(bookingRepository).deleteById("booking-1");
    }

    @Test
    void modifyBookingRejectsSeatAlreadyTakenByAnotherBooking() {
        Booking booking = booking("booking-1", List.of("C1"), 200.0);
        Booking anotherBooking = booking("booking-2", List.of("C2"), 200.0);
        when(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking));
        when(bookingRepository.findByMovieIdAndTheaterAndDateAndTime(
                "movie-1", "INOX", "2026-10-10", "06:31 PM - 09:31 PM"))
                .thenReturn(List.of(booking, anotherBooking));

        ResponseEntity<?> response = bookingController.modifyBooking(
                "booking-1",
                new BookingController.ModifyBookingRequest(
                        List.of(),
                        List.of(new BookingController.SeatChange("C1", "C2"))));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    void createBookingWithFoodItemsCalculatesTotalsAndSetsConfirmedStatus() {
        Booking booking = booking("booking-food", List.of("A1"), 0.0);
        booking.setTicketTotal(200.0);
        booking.setFoodItems(List.of(
                new FoodOrderItem("food-1", "Caramel Popcorn", 150.0, 2, "img1", "Popcorn"),
                new FoodOrderItem("food-2", "Pepsi", 50.0, 1, "img2", "Beverages")
        ));
        booking.setTax(27.5);
        when(bookingRepository.findByMovieIdAndTheaterAndDateAndTime(
                "movie-1", "INOX", "2026-10-10", "06:31 PM - 09:31 PM"))
                .thenReturn(List.of());
        when(bookingRepository.save(booking)).thenReturn(booking);

        ResponseEntity<?> response = bookingController.createBooking(booking);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(350.0, booking.getFoodTotal());
        assertEquals(577.5, booking.getTotal());
        assertEquals("CONFIRMED", booking.getStatus());
        verify(bookingRepository).save(booking);
    }

    @Test
    void cancelBookingFutureShowCalculates15PercentFeeAndRefunds85Percent() {
        Booking booking = booking("booking-cancel", List.of("A1", "A2"), 500.0);
        booking.setDate(LocalDate.now().plusDays(2).toString());
        booking.setTime("08:00 PM");
        booking.setStatus("CONFIRMED");
        when(bookingRepository.findById("booking-cancel")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);

        ResponseEntity<?> response = bookingController.cancelBooking(
                "booking-cancel",
                new BookingController.CancelBookingRequest("ALL"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        BookingController.CancelBookingResponse body =
                assertInstanceOf(BookingController.CancelBookingResponse.class, response.getBody());
        assertEquals("CANCELLED", body.status());
        assertEquals(500.0, body.originalAmount());
        assertEquals(75.0, body.cancellationFee()); // 15% of 500
        assertEquals(425.0, body.refundAmount());    // 85% of 500
        assertEquals("CANCELLED", booking.getStatus());
        assertEquals(425.0, booking.getRefundAmount());
        assertEquals(75.0, booking.getCancellationFee());
    }

    @Test
    void cancelBookingPastShowIsRejectedWith400() {
        Booking booking = booking("booking-past", List.of("A1"), 200.0);
        booking.setDate("2020-01-01");
        booking.setTime("10:00 AM");
        when(bookingRepository.findById("booking-past")).thenReturn(Optional.of(booking));

        ResponseEntity<?> response = bookingController.cancelBooking(
                "booking-past",
                new BookingController.CancelBookingRequest("ALL"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(bookingRepository, never()).save(booking);
    }

    @Test
    void cancelBookingAlreadyCancelledIsRejectedWith400() {
        Booking booking = booking("booking-done", List.of("A1"), 200.0);
        booking.setDate(LocalDate.now().plusDays(3).toString());
        booking.setTime("07:00 PM");
        booking.setStatus("CANCELLED");
        when(bookingRepository.findById("booking-done")).thenReturn(Optional.of(booking));

        ResponseEntity<?> response = bookingController.cancelBooking(
                "booking-done",
                new BookingController.CancelBookingRequest("ALL"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(bookingRepository, never()).save(booking);
    }

    @Test
    void cancelFoodOnlyCancelsFoodAndRefundsPortionMinusFee() {
        Booking booking = booking("booking-food-cancel", List.of("A1"), 600.0);
        booking.setDate(LocalDate.now().plusDays(2).toString());
        booking.setTime("06:00 PM");
        booking.setStatus("CONFIRMED");
        booking.setFoodTotal(200.0);
        booking.setFoodItems(new ArrayList<>(List.of(
                new FoodOrderItem("food-1", "Popcorn", 200.0, 1, "img", "Popcorn")
        )));
        when(bookingRepository.findById("booking-food-cancel")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);

        ResponseEntity<?> response = bookingController.cancelBooking(
                "booking-food-cancel",
                new BookingController.CancelBookingRequest("FOOD_ONLY"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        BookingController.CancelBookingResponse body =
                assertInstanceOf(BookingController.CancelBookingResponse.class, response.getBody());
        assertEquals("FOOD_CANCELLED", body.status());
        assertEquals(200.0, body.originalAmount());
        assertEquals(30.0, body.cancellationFee()); // 15% of 200
        assertEquals(170.0, body.refundAmount());   // 85% of 200
        assertEquals(400.0, booking.getTotal());
        assertEquals(0.0, booking.getFoodTotal());
    }

    @Test
    void getBookedSeatsIgnoresCancelledBookingsReleasingSeatsBack() {
        Booking active = booking("b-1", List.of("A1", "A2"), 400.0);
        active.setStatus("CONFIRMED");
        Booking cancelled = booking("b-2", List.of("A3", "A4"), 400.0);
        cancelled.setStatus("CANCELLED");

        when(bookingRepository.findByMovieIdAndTheaterAndDateAndTime(
                "movie-1", "INOX", "2026-10-10", "06:31 PM - 09:31 PM"))
                .thenReturn(List.of(active, cancelled));

        ResponseEntity<List<String>> response = bookingController.getBookedSeats(
                "movie-1", "INOX", "2026-10-10", "06:31 PM - 09:31 PM");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of("A1", "A2"), response.getBody());
    }

    private Booking booking(String id, List<String> seats, double total) {
        Booking booking = new Booking();
        booking.setId(id);
        booking.setMovieId("movie-1");
        booking.setTheater("INOX");
        booking.setDate("2026-10-10");
        booking.setTime("06:31 PM - 09:31 PM");
        booking.setSeats(seats);
        booking.setTotal(total);
        return booking;
    }
}
