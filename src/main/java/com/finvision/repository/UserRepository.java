package com.finvision.repository;

import com.finvision.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

/**
 * MongoDB repository for {@link com.finvision.model.User} documents.
 *
 * <p>Extends {@link MongoRepository} to provide standard CRUD operations and
 * adds a derived query to look up users by their unique username.</p>
 */
public interface UserRepository extends MongoRepository<User, String> {

    /**
     * Finds a user by their unique username.
     *
     * @param username the username to search for
     * @return an {@link Optional} containing the user if found
     */
    Optional<User> findByUsername(String username);
}
