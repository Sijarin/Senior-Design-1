package com.finvision.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "scanned_receipts")
@CompoundIndexes({
        @CompoundIndex(name = "username_yearmonth_idx", def = "{'username': 1, 'yearMonth': 1}")
})
public class ScannedReceipt {

    @Id
    private String id;

    private String username;
    private String merchantName;
    private double amount;
    private String category; // Groceries, Dining, Shopping, Transport, Healthcare, Entertainment, Other
    private String dateStr; // ISO "YYYY-MM-DD" — easy to sort/display
    private String yearMonth; // "YYYY-MM" — used for fast month filtering
    private String notes;
    private String imageData; // base64 thumbnail (optional)

    // ---------- Getters / Setters ----------
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public void setMerchantName(String merchantName) {
        this.merchantName = merchantName;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDateStr() {
        return dateStr;
    }

    public void setDateStr(String dateStr) {
        this.dateStr = dateStr;
    }

    public String getYearMonth() {
        return yearMonth;
    }

    public void setYearMonth(String yearMonth) {
        this.yearMonth = yearMonth;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getImageData() {
        return imageData;
    }

    public void setImageData(String imageData) {
        this.imageData = imageData;
    }
}
