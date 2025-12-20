package com.stockpulse.agent.repository;

import com.stockpulse.agent.model.PaperTradingAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaperTradingAccountRepository extends JpaRepository<PaperTradingAccount, Long> {

    Optional<PaperTradingAccount> findByAccountName(String accountName);
}
