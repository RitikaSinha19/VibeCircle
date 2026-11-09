package com.vibecircle.interest;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterestRepository extends JpaRepository<Interest, Long> {
    List<Interest> findAllByOrderByCategoryAscNameAsc();
}
