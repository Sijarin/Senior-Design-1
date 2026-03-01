package com.finvision.repository;

import com.finvision.model.Bill;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface BillRepository extends MongoRepository<Bill, String> {
    List<Bill> findByUsernameOrderByDueDateAsc(String username);
    Optional<Bill> findByIdAndUsername(String id, String username);
    List<Bill> findByUsernameAndBillNameIgnoreCase(String username, String billName);
}
