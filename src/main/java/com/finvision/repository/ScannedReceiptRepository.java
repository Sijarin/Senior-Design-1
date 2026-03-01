package com.finvision.repository;

import com.finvision.model.ScannedReceipt;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ScannedReceiptRepository extends MongoRepository<ScannedReceipt, String> {

    /** All receipts for a user, newest first */
    List<ScannedReceipt> findByUsernameOrderByDateStrDesc(String username);

    /** Receipts for a specific month, e.g. "2026-02" */
    List<ScannedReceipt> findByUsernameAndYearMonth(String username, String yearMonth);
}
