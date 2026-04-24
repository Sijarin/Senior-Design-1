package com.finvision.repository;

import com.finvision.model.Bill;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB repository for {@link Bill} documents.
 *
 * <p>Provides CRUD operations via {@link MongoRepository} plus custom
 * derived queries for per-user bill lookups.</p>
 */
public interface BillRepository extends MongoRepository<Bill, String> {

    /**
     * Returns all bills for the given user, sorted by due date ascending.
     *
     * @param username the authenticated user's username
     * @return list of bills ordered by earliest due date first
     */
    List<Bill> findByUsernameOrderByDueDateAsc(String username);

    /**
     * Finds a single bill by its ID that also belongs to the specified user.
     * Used to prevent users from accessing each other's bills.
     *
     * @param id       the bill's MongoDB document ID
     * @param username the authenticated user's username
     * @return an {@link Optional} containing the bill if found and owned by the user
     */
    Optional<Bill> findByIdAndUsername(String id, String username);

    /**
     * Finds bills for a user whose name matches, case-insensitively.
     * Used to detect potential duplicate or recurring bills.
     *
     * @param username the authenticated user's username
     * @param billName the bill name to search (case-insensitive)
     * @return list of matching bills
     */
    List<Bill> findByUsernameAndBillNameIgnoreCase(String username, String billName);
}
