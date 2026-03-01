package com.finvision.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;

@Document(collection = "budgets")
public class Budget {

    @Id
    private String id;

    private String username;
    private String month;

    // Income
    private double monthlyIncome;
    private double otherIncome;

    // Fixed Expenses
    private double rent;
    private double utilities;
    private double insurance;
    private double groceries;
    private double subscriptions;
    private String rentDueDate;
    private String utilitiesDueDate;
    private String insuranceDueDate;
    private String groceriesDueDate;
    private String subscriptionsDueDate;

    // Variable Expenses
    private List<String> variableTitle;
    private List<Double> variableAmount;
    private List<String> variableDueDate;

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getMonth() { return month; }
    public void setMonth(String month) { this.month = month; }

    public double getMonthlyIncome() { return monthlyIncome; }
    public void setMonthlyIncome(double monthlyIncome) { this.monthlyIncome = monthlyIncome; }

    public double getOtherIncome() { return otherIncome; }
    public void setOtherIncome(double otherIncome) { this.otherIncome = otherIncome; }

    public double getRent() { return rent; }
    public void setRent(double rent) { this.rent = rent; }

    public double getUtilities() { return utilities; }
    public void setUtilities(double utilities) { this.utilities = utilities; }

    public double getInsurance() { return insurance; }
    public void setInsurance(double insurance) { this.insurance = insurance; }

    public double getGroceries() { return groceries; }
    public void setGroceries(double groceries) { this.groceries = groceries; }

    public double getSubscriptions() { return subscriptions; }
    public void setSubscriptions(double subscriptions) { this.subscriptions = subscriptions; }

    public String getRentDueDate() { return rentDueDate; }
    public void setRentDueDate(String rentDueDate) { this.rentDueDate = rentDueDate; }

    public String getUtilitiesDueDate() { return utilitiesDueDate; }
    public void setUtilitiesDueDate(String utilitiesDueDate) { this.utilitiesDueDate = utilitiesDueDate; }

    public String getInsuranceDueDate() { return insuranceDueDate; }
    public void setInsuranceDueDate(String insuranceDueDate) { this.insuranceDueDate = insuranceDueDate; }

    public String getGroceriesDueDate() { return groceriesDueDate; }
    public void setGroceriesDueDate(String groceriesDueDate) { this.groceriesDueDate = groceriesDueDate; }

    public String getSubscriptionsDueDate() { return subscriptionsDueDate; }
    public void setSubscriptionsDueDate(String subscriptionsDueDate) { this.subscriptionsDueDate = subscriptionsDueDate; }

    public List<String> getVariableTitle() { return variableTitle; }
    public void setVariableTitle(List<String> variableTitle) { this.variableTitle = variableTitle; }

    public List<Double> getVariableAmount() { return variableAmount; }
    public void setVariableAmount(List<Double> variableAmount) { this.variableAmount = variableAmount; }

    public List<String> getVariableDueDate() { return variableDueDate; }
    public void setVariableDueDate(List<String> variableDueDate) { this.variableDueDate = variableDueDate; }
}
