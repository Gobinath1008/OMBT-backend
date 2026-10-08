package com.omtb.backend.repositories;
import com.omtb.backend.models.Movie;
import org.springframework.data.mongodb.repository.MongoRepository;
public interface MovieRepository extends MongoRepository<Movie, String> {}