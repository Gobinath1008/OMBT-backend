package com.omtb.backend.models;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;

@Data
@Document(collection = "bookings")
@CompoundIndex(
    name = "booking_show_lookup_idx",
    def = "{'movieId': 1, 'theater': 1, 'date': 1, 'time': 1}"
)
public class Booking {
    @Id
    private String id;
    private String movieId;
    private String movieName;
    private String theater;
    private String date;
    private String time;
    private List<String> seats;
    private Double total;
    @Indexed(name = "booking_user_lookup_idx")
    private String userId;
    private String userName;
    private String userEmail;
    private String bookedAt;
}
