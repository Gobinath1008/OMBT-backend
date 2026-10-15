package com.omtb.backend.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "foods")
public class Food {
    @Id
    private String id;
    private String name;
    private String description;
    private Double price;
    private String imageURL;
    private String category;
}

