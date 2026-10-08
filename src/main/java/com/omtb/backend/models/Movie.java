package com.omtb.backend.models;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;

@Data
@Document(collection = "movies")
public class Movie {
    @Id
    private String id;
    private String name;
    private String description;
    private String genre;
    private String rating;
    private String img;
    private Integer rate;
    private Boolean isHero;
    private List<Theater> theaters;
}
