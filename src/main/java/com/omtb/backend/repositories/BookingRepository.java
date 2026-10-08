package com.omtb.backend.repositories;

import com.omtb.backend.models.Booking;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface BookingRepository extends MongoRepository<Booking, String> {
    List<Booking> findByUserId(String userId);
    List<Booking> findByUserIdOrderByBookedAtDescIdDesc(String userId);
    List<Booking> findByMovieIdAndTheaterAndDateAndTime(String movieId, String theater, String date, String time);
}
