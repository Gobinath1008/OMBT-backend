package com.omtb.backend.models;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "seats")
public class Seat {
    @Id private String id;
    private String screenId;
    private String seatNumber;
    private String row;
    private String seatType = "regular";
    private Double price;
}