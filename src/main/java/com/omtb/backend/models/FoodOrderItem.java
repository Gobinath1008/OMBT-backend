package com.omtb.backend.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FoodOrderItem {
    private String foodId;
    private String name;
    private Double price;
    private Integer quantity;
    private String imageURL;
    private String category;
}

