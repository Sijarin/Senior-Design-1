package com.finvision.repository;

import com.finvision.model.Budget;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

/**
 * MongoDB repository for {@link Budget} documents.
 *
 * <p>Supports per-user, per-month budget lookups as well as retrieving full
 * budget history in reverse chronological order.</p>
 */
public interface BudgetRepository extends MongoRepository<Budget, String> {

    /**
     * Finds a budget for a specific user and calendar month (e.g., "April 2026").
     *
     * @param username the authenticated user's username
     * @param month    the month label stored in the document
     * @return an {@link Optional} containing the budget if one exists for that month
     */
    Optional<Budget> findByUsernameAndMonth(String username, String month);

    /**
     * Returns the most recently created budget for the given user.
     * Used to pre-fill the dashboard with the latest budget data.
     *
     * @param username the authenticated user's username
     * @return an {@link Optional} containing the newest budget document
     */
    Optional<Budget> findTopByUsernameOrderByIdDesc(String username);

    /**
     * Returns all budgets for a user, newest first.
     * Used on the Budget History page.
     *
     * @param username the authenticated user's username
     * @return list of all budget documents in descending creation order
     */
    List<Budget> findByUsernameOrderByIdDesc(String username);

    /**
     * Deletes all budget records belonging to the given user.
     * Called when a user deletes their account.
     *
     * @param username the username whose budgets should be removed
     */
    void deleteByUsername(String username);
}
