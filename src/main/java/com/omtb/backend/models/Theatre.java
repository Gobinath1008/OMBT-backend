package com.omtb.backend.models;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "theatres")
public class Theatre {
    @Id private String id;
    private String name;
    private String location;
    private String city;
    private String address;
    private String status = "active";
}