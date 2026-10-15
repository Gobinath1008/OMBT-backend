package com.omtb.backend.repositories;

import com.omtb.backend.models.Food;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface FoodRepository extends MongoRepository<Food, String> {
    List<Food> findByCategory(String category);
}

