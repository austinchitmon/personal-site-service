package com.chitmon.backend.users;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UserRepository extends JpaRepository<User, UUID> {

    // @Modifying queries aren't auto-wrapped in a transaction the way inherited
    // SimpleJpaRepository CRUD methods are, hence the explicit @Transactional.
    @Modifying
    @Transactional
    @Query(value = """
            insert into core.users (id, email, display_name, avatar_url, created_at, updated_at)
            values (:id, :email, :displayName, :avatarUrl, now(), now())
            on conflict (id) do update set
                email = excluded.email,
                display_name = excluded.display_name,
                avatar_url = excluded.avatar_url,
                updated_at = now()
            """, nativeQuery = true)
    void upsert(@Param("id") UUID id, @Param("email") String email,
            @Param("displayName") String displayName, @Param("avatarUrl") String avatarUrl);
}
