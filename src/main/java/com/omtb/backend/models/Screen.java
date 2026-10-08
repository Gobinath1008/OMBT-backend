package com.omtb.backend.models;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "screens")
public class Screen {
    @Id private String id;
    private String theatreId;
    private String name;
    private Integer totalSeats;
}