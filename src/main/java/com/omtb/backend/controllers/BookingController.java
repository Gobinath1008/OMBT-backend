package com.omtb.backend.controllers;

import com.omtb.backend.models.Booking;
import com.omtb.backend.repositories.BookingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/bookings")
@CrossOrigin(origins = "*")
public class BookingController {
    private static final double VIP_SURCHARGE = 50.0;
    private static final Set<String> VIP_ROWS = Set.of("A", "B");
    private static final double CANCELLATION_TAX_FEE_RATE = 0.15; // 15% cancellation fee

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

    public record CancelBookingRequest(String cancelType) {} // "ALL" or "FOOD_ONLY"

    public record CancelBookingResponse(
            String bookingId,
            String status,
            double originalAmount,
            double cancellationFee,
            double refundAmount,
            String message,
            Booking booking) {}

    @GetMapping("/seats")
    public ResponseEntity<List<String>> getBookedSeats(
            @RequestParam String movieId,
            @RequestParam String theater,
            @RequestParam String date,
            @RequestParam String time) {
        
        List<Booking> bookings = bookingRepository.findByMovieIdAndTheaterAndDateAndTime(movieId, theater, date, time);
        List<String> bookedSeats = bookings.stream()
                .filter(b -> !"CANCELLED".equalsIgnoreCase(b.getStatus()))
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
                .filter(existing -> !"CANCELLED".equalsIgnoreCase(existing.getStatus()))
                .filter(existing -> existing.getSeats() != null)
                .flatMap(existing -> existing.getSeats().stream())
                .anyMatch(requestedSeats::contains);
        if (seatTaken) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "One or more selected seats have already been booked. Please choose different seats."));
        }

        // Calculate food total if food items are provided
        if (booking.getFoodItems() != null && !booking.getFoodItems().isEmpty()) {
            double calcFood = booking.getFoodItems().stream()
                    .mapToDouble(i -> (i.getPrice() != null ? i.getPrice() : 0.0) * (i.getQuantity() != null ? i.getQuantity() : 1))
                    .sum();
            booking.setFoodTotal(Math.round(calcFood * 100.0) / 100.0);
        } else if (booking.getFoodTotal() == null) {
            booking.setFoodTotal(0.0);
        }

        // Calculate ticket total and tax
        if (booking.getTicketTotal() == null) {
            if (booking.getTotal() != null && booking.getTotal() > 0) {
                double derivedTicket = booking.getTotal() - booking.getFoodTotal() - (booking.getTax() != null ? booking.getTax() : 0.0);
                booking.setTicketTotal(Math.max(0.0, Math.round(derivedTicket * 100.0) / 100.0));
            }
        }

        // If ticketTotal or tax is explicitly supplied, calculate grand total
        if (booking.getTicketTotal() != null) {
            double tax = booking.getTax() != null ? booking.getTax() : Math.round((booking.getTicketTotal() + booking.getFoodTotal()) * 0.05 * 100.0) / 100.0;
            booking.setTax(tax);
            if (booking.getTotal() == null || booking.getTotal() == 0.0) {
                booking.setTotal(Math.round((booking.getTicketTotal() + booking.getFoodTotal() + tax) * 100.0) / 100.0);
            }
        }

        if (booking.getStatus() == null) {
            booking.setStatus("CONFIRMED");
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

    /**
     * Cancellation endpoint with refund policy validation.
     * Rule 1: Strictly before show date and time.
     * Rule 2: 15% cancellation tax/fee deducted, balance 85% refunded.
     * Rule 3: Releasing booked seats back to the available pool.
     * Supports: cancelType = "ALL" (tickets + food) or "FOOD_ONLY".
     */
    @PostMapping("/{bookingId}/cancel")
    public ResponseEntity<?> cancelBooking(
            @PathVariable String bookingId,
            @RequestBody(required = false) CancelBookingRequest request) {

        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null) {
            return ResponseEntity.notFound().build();
        }

        if ("CANCELLED".equalsIgnoreCase(booking.getStatus())) {
            return badRequest("This booking has already been cancelled.");
        }

        // Validate time constraint: cancellation is ONLY allowed strictly before show date and time
        LocalDateTime showDateTime = parseShowDateTime(booking.getDate(), booking.getTime());
        if (showDateTime != null && !LocalDateTime.now().isBefore(showDateTime)) {
            return badRequest("Cancellation is not allowed. The show date and time has already started or passed.");
        }

        String cancelType = (request != null && request.cancelType() != null)
                ? request.cancelType().trim().toUpperCase()
                : "ALL";

        if ("FOOD_ONLY".equals(cancelType)) {
            if (booking.getFoodTotal() == null || booking.getFoodTotal() <= 0.0) {
                return badRequest("This booking does not contain any active food items to cancel.");
            }
            if ("FOOD_CANCELLED".equalsIgnoreCase(booking.getStatus())) {
                return badRequest("Food items for this booking have already been cancelled.");
            }

            double originalAmount = booking.getFoodTotal();
            double cancellationFee = Math.round(originalAmount * CANCELLATION_TAX_FEE_RATE * 100.0) / 100.0;
            double refundAmount = Math.max(0.0, Math.round((originalAmount - cancellationFee) * 100.0) / 100.0);

            booking.setFoodTotal(0.0);
            booking.setFoodItems(new ArrayList<>());
            double currentTotal = booking.getTotal() != null ? booking.getTotal() : 0.0;
            booking.setTotal(Math.max(0.0, Math.round((currentTotal - originalAmount) * 100.0) / 100.0));
            booking.setStatus("FOOD_CANCELLED");
            booking.setRefundAmount((booking.getRefundAmount() != null ? booking.getRefundAmount() : 0.0) + refundAmount);
            booking.setCancellationFee((booking.getCancellationFee() != null ? booking.getCancellationFee() : 0.0) + cancellationFee);

            Booking saved = bookingRepository.save(booking);
            return ResponseEntity.ok(new CancelBookingResponse(
                    bookingId,
                    "FOOD_CANCELLED",
                    originalAmount,
                    cancellationFee,
                    refundAmount,
                    "Food items cancelled successfully. A refund of ₹" + refundAmount + " has been processed.",
                    saved));
        }

        // Cancel entire booking (Tickets + Food)
        double originalAmount = booking.getTotal() != null ? booking.getTotal() : 0.0;
        double cancellationFee = Math.round(originalAmount * CANCELLATION_TAX_FEE_RATE * 100.0) / 100.0;
        double refundAmount = Math.max(0.0, Math.round((originalAmount - cancellationFee) * 100.0) / 100.0);

        booking.setStatus("CANCELLED");
        booking.setRefundAmount(refundAmount);
        booking.setCancellationFee(cancellationFee);
        booking.setCancelledAt(Instant.now().toString());

        Booking saved = bookingRepository.save(booking);
        return ResponseEntity.ok(new CancelBookingResponse(
                bookingId,
                "CANCELLED",
                originalAmount,
                cancellationFee,
                refundAmount,
                "Booking cancelled successfully. A refund of ₹" + refundAmount + " has been processed.",
                saved));
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
                .filter(other -> !"CANCELLED".equalsIgnoreCase(other.getStatus()))
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

    public static LocalDateTime parseShowDateTime(String dateStr, String timeStr) {
        if (dateStr == null || timeStr == null || dateStr.isBlank() || timeStr.isBlank()) {
            return null;
        }
        LocalDate date = null;
        String cleanDate = dateStr.trim();
        try {
            if (cleanDate.matches("^\\d{4}-\\d{1,2}-\\d{1,2}$")) {
                date = LocalDate.parse(cleanDate, DateTimeFormatter.ofPattern("yyyy-M-d"));
            } else if (cleanDate.matches("^\\d{1,2}-\\d{1,2}-\\d{4}$")) {
                date = LocalDate.parse(cleanDate, DateTimeFormatter.ofPattern("d-M-yyyy"));
            } else if (cleanDate.matches("^\\d{1,2}/\\d{1,2}/\\d{4}$")) {
                date = LocalDate.parse(cleanDate, DateTimeFormatter.ofPattern("d/M/yyyy"));
            }
        } catch (Exception ignored) {
            return null;
        }

        if (date == null) {
            return null;
        }

        String startTimeStr = timeStr.split("-")[0].trim().toUpperCase();
        LocalTime time = null;
        String[] patterns = {"h:mm a", "hh:mm a", "h:mma", "hh:mma", "H:mm", "HH:mm"};
        for (String pattern : patterns) {
            try {
                time = LocalTime.parse(startTimeStr, DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH));
                break;
            } catch (Exception ignored) {
            }
        }

        if (time == null) {
            return null;
        }

        return LocalDateTime.of(date, time);
    }
}
