package com.omtb.backend.models;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDate;

@Data
@Document(collection = "shows")
public class Show {
    @Id private String id;
    private String movieId;
    private String screenId;
    private LocalDate showDate;
    private String startTime;
    private String endTime;
    private Double ticketPrice;
    private String status = "active";
}