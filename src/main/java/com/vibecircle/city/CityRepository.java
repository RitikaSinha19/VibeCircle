package com.vibecircle.city;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CityRepository extends JpaRepository<City, Long> {
    // StartingWith escapes % and _ in the input for us
    List<City> findByNameStartingWithIgnoreCase(String prefix, Pageable pageable);
}
