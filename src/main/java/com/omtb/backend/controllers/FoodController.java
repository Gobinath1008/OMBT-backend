package com.omtb.backend.controllers;

import com.omtb.backend.models.Food;
import com.omtb.backend.repositories.FoodRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/food", "/api/foods"})
@CrossOrigin(origins = "*")
public class FoodController {

    @Autowired
    private FoodRepository foodRepository;

    @PostConstruct
    public void seedDefaultFoods() {
        if (foodRepository.count() == 0) {
            List<Food> initialFoods = List.of(
                new Food(null, "Caramel Popcorn (Large)", "Freshly popped crispy gourmet popcorn coated with rich golden caramel.", 220.0, "https://images.unsplash.com/photo-1578849278619-e73505e9610f?w=600&auto=format&fit=crop&q=80", "Popcorn"),
                new Food(null, "Cheese & Butter Popcorn (Medium)", "Golden cinema-style popcorn loaded with real melted cheese & creamy butter.", 190.0, "https://images.unsplash.com/photo-1505686994434-e3cc5abf1330?w=600&auto=format&fit=crop&q=80", "Popcorn"),
                new Food(null, "Loaded Mexican Nachos", "Crisp seasoned tortilla chips served with warm melted jalapeno queso & zesty tomato salsa.", 180.0, "https://images.unsplash.com/photo-1513456852971-30c0b8199d4d?w=600&auto=format&fit=crop&q=80", "Snacks"),
                new Food(null, "Crispy Peri-Peri Fries", "Freshly fried golden potato fries dusted with spicy African peri-peri seasoning and creamy garlic mayo.", 140.0, "https://images.unsplash.com/photo-1576107232684-1279f3908594?w=600&auto=format&fit=crop&q=80", "Snacks"),
                new Food(null, "Paneer Tikka Burger", "Crispy spiced paneer patty with mint mayo, crisp lettuce, tomato, and a soft toasted brioche bun.", 160.0, "https://images.unsplash.com/photo-1568901346375-23c9450c58cd?w=600&auto=format&fit=crop&q=80", "Snacks"),
                new Food(null, "Chilled Pepsi Fountain Drink (500ml)", "Refreshing fountain soda poured ice-cold and bubbly.", 90.0, "https://images.unsplash.com/photo-1622483767028-3f66f32aef97?w=600&auto=format&fit=crop&q=80", "Beverages"),
                new Food(null, "Iced Chocolate Cold Coffee", "Creamy blended espresso cold brew topped with dark chocolate drizzle.", 130.0, "https://images.unsplash.com/photo-1517256064527-09c73fc73e38?w=600&auto=format&fit=crop&q=80", "Beverages"),
                new Food(null, "Blockbuster Cinema Duo Combo", "1 Large Caramel Popcorn + 2 Chilled Pepsis (500ml) + 1 Loaded Nachos tub.", 450.0, "https://images.unsplash.com/photo-1585647347384-2593bc35786b?w=600&auto=format&fit=crop&q=80", "Combos")
            );
            foodRepository.saveAll(initialFoods);
        }
    }

    @GetMapping
    public List<Food> getAllFoods() {
        return foodRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Food> getFoodById(@PathVariable String id) {
        return foodRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/category/{category}")
    public List<Food> getFoodsByCategory(@PathVariable String category) {
        return foodRepository.findByCategory(category);
    }

    @PostMapping
    public ResponseEntity<Food> createFood(@RequestBody Food food) {
        Food saved = foodRepository.save(food);
        return ResponseEntity.ok(saved);
    }
}

