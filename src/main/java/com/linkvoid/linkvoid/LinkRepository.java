package com.linkvoid.linkvoid;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface LinkRepository extends JpaRepository<Link, Long> {
    
    // Método buscar un enlace por su código corto (ej: "abc12")
    Optional<Link> findByShortCode(String shortCode);

    // Método para buscar por la URL original y evitar duplicados
    Optional<Link> findByOriginalUrl(String originalUrl);
}