package com.example.uniactivity.repository;

import com.example.uniactivity.entity.OAuthExchangeCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OAuthExchangeCodeRepository extends JpaRepository<OAuthExchangeCode, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select code from OAuthExchangeCode code join fetch code.user where code.codeHash = :codeHash")
    Optional<OAuthExchangeCode> findByCodeHashForUpdate(@Param("codeHash") String codeHash);
}
