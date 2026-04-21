package com.finvision.repository;

import com.finvision.model.Budget;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends MongoRepository<Budget, String> {
    Optional<Budget> findByUsernameAndMonth(String username, String month);
    Optional<Budget> findTopByUsernameOrderByIdDesc(String username);
    List<Budget> findByUsernameOrderByIdDesc(String username);
    void deleteByUsername(String username);
}
