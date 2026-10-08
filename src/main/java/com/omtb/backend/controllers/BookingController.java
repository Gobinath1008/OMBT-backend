package com.omtb.backend.controllers;

import com.omtb.backend.models.Booking;
import com.omtb.backend.repositories.BookingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/bookings")
@CrossOrigin(origins = "*")
public class BookingController {
    private static final double VIP_SURCHARGE = 50.0;
    private static final Set<String> VIP_ROWS = Set.of("A", "B");

    @Autowired
    private BookingRepository bookingRepository;

    public record SeatChange(String from, String to) {}

    public record ModifyBookingRequest(List<String> cancelSeats, List<SeatChange> changeSeats) {}

    public record ModifyBookingResponse(
            Booking booking,
            boolean bookingCancelled,
            double refundAmount,
            double priceDifference,
            String message) {}

    @GetMapping("/seats")
    public ResponseEntity<List<String>> getBookedSeats(
            @RequestParam String movieId,
            @RequestParam String theater,
            @RequestParam String date,
            @RequestParam String time) {
        
        List<Booking> bookings = bookingRepository.findByMovieIdAndTheaterAndDateAndTime(movieId, theater, date, time);
        List<String> bookedSeats = bookings.stream()
                .filter(b -> b.getSeats() != null)
                .flatMap(b -> b.getSeats().stream())
                .distinct()
                .collect(Collectors.toList());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(bookedSeats);
    }

    @GetMapping
    public List<Booking> getAllBookings() {
        return bookingRepository.findAll();
    }

    @GetMapping("/user/{userId}")
    public List<Booking> getUserBookings(@PathVariable String userId) {
        return bookingRepository.findByUserIdOrderByBookedAtDescIdDesc(userId);
    }

    @PostMapping
    public ResponseEntity<?> createBooking(@RequestBody Booking booking) {
        if (booking.getSeats() == null || booking.getSeats().isEmpty()) {
            return badRequest("Select at least one seat to book.");
        }
        Set<String> requestedSeats = new HashSet<>(booking.getSeats());
        if (requestedSeats.size() != booking.getSeats().size() || requestedSeats.contains(null)) {
            return badRequest("The booking contains invalid or duplicate seats.");
        }

        boolean seatTaken = bookingRepository.findByMovieIdAndTheaterAndDateAndTime(
                        booking.getMovieId(), booking.getTheater(), booking.getDate(), booking.getTime())
                .stream()
                .filter(existing -> existing.getSeats() != null)
                .flatMap(existing -> existing.getSeats().stream())
                .anyMatch(requestedSeats::contains);
        if (seatTaken) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "One or more selected seats have already been booked. Please choose different seats."));
        }

        booking.setBookedAt(Instant.now().toString());
        Booking savedBooking = bookingRepository.save(booking);
        return ResponseEntity.ok(savedBooking);
    }

    @PutMapping
    public ResponseEntity<Booking> updateBooking(@RequestBody Booking booking) {
        if (!bookingRepository.existsById(booking.getId())) {
            return ResponseEntity.notFound().build();
        }
        Booking updatedBooking = bookingRepository.save(booking);
        return ResponseEntity.ok(updatedBooking);
    }

    @DeleteMapping
    public ResponseEntity<?> deleteBooking(@RequestBody Booking booking) {
        if (!bookingRepository.existsById(booking.getId())) {
            return ResponseEntity.notFound().build();
        }
        bookingRepository.deleteById(booking.getId());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{bookingId}/modify")
    public ResponseEntity<?> modifyBooking(
            @PathVariable String bookingId,
            @RequestBody ModifyBookingRequest request) {
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null) {
            return ResponseEntity.notFound().build();
        }
        if (request == null || booking.getSeats() == null || booking.getSeats().isEmpty()) {
            return badRequest("The booking has no seats to modify.");
        }

        List<String> cancelSeats = request.cancelSeats() == null ? List.of() : request.cancelSeats();
        List<SeatChange> changes = request.changeSeats() == null ? List.of() : request.changeSeats();
        Set<String> currentSeats = new HashSet<>(booking.getSeats());
        Set<String> affectedSeats = new HashSet<>();

        for (String seat : cancelSeats) {
            if (seat == null || !currentSeats.contains(seat) || !affectedSeats.add(seat)) {
                return badRequest("A cancelled seat is invalid or duplicated.");
            }
        }
        for (SeatChange change : changes) {
            if (change == null || change.from() == null || change.to() == null
                    || !currentSeats.contains(change.from()) || currentSeats.contains(change.to())
                    || !affectedSeats.add(change.from())) {
                return badRequest("A seat change is invalid or duplicated.");
            }
        }
        if (affectedSeats.isEmpty()) {
            return badRequest("Select at least one seat to cancel or change.");
        }

        Set<String> replacementSeats = new HashSet<>();
        for (SeatChange change : changes) {
            if (!replacementSeats.add(change.to())) {
                return badRequest("A replacement seat cannot be selected more than once.");
            }
        }

        boolean seatTaken = bookingRepository.findByMovieIdAndTheaterAndDateAndTime(
                        booking.getMovieId(), booking.getTheater(), booking.getDate(), booking.getTime())
                .stream()
                .filter(other -> !bookingId.equals(other.getId()))
                .filter(other -> other.getSeats() != null)
                .flatMap(other -> other.getSeats().stream())
                .anyMatch(replacementSeats::contains);
        if (seatTaken) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "One or more replacement seats have already been booked."));
        }

        Map<String, Double> seatPrices = getSeatPrices(booking);
        double refundAmount = cancelSeats.stream().mapToDouble(seatPrices::get).sum();
        double priceDifference = changes.stream()
                .mapToDouble(change -> seatPrice(
                        change.to(),
                        seatPrices.get(change.from()) - (isVip(change.from()) ? VIP_SURCHARGE : 0.0))
                        - seatPrices.get(change.from()))
                .sum();

        List<String> updatedSeats = booking.getSeats().stream()
                .filter(seat -> !cancelSeats.contains(seat))
                .filter(seat -> changes.stream().noneMatch(change -> change.from().equals(seat)))
                .collect(Collectors.toList());
        updatedSeats.addAll(changes.stream().map(SeatChange::to).toList());

        if (updatedSeats.isEmpty()) {
            bookingRepository.deleteById(bookingId);
            return ResponseEntity.ok(new ModifyBookingResponse(
                    null, true, refundAmount, priceDifference, "Booking cancelled successfully."));
        }

        booking.setSeats(updatedSeats);
        double currentTotal = booking.getTotal() == null ? 0.0 : booking.getTotal();
        booking.setTotal(Math.max(0.0, currentTotal - refundAmount + priceDifference));
        Booking updatedBooking = bookingRepository.save(booking);
        return ResponseEntity.ok(new ModifyBookingResponse(
                updatedBooking, false, refundAmount, priceDifference, "Booking updated successfully."));
    }

    private ResponseEntity<Map<String, String>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    private Map<String, Double> getSeatPrices(Booking booking) {
        long vipSeats = booking.getSeats().stream().filter(this::isVip).count();
        double total = booking.getTotal() == null ? 0.0 : booking.getTotal();
        double basePrice = (total - VIP_SURCHARGE * vipSeats) / booking.getSeats().size();
        return booking.getSeats().stream().collect(Collectors.toMap(
                seat -> seat,
                seat -> seatPrice(seat, basePrice)));
    }

    private double seatPrice(String seat, double basePrice) {
        return basePrice + (isVip(seat) ? VIP_SURCHARGE : 0.0);
    }

    private boolean isVip(String seat) {
        return seat != null && VIP_ROWS.contains(seat.substring(0, 1));
    }
}
