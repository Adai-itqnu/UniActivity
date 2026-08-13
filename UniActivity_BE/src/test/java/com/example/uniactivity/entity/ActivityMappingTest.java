package com.example.uniactivity.entity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

@DataJpaTest(showSql = false)
@ActiveProfiles("test")
class ActivityMappingTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    void activityMetadataBootsWithCreatedByIndex() {
        assertThat(entityManager.getMetamodel().entity(Activity.class)).isNotNull();
    }

    @Test
    void createdByIndexUsesTheMappedJoinColumn() throws NoSuchFieldException {
        String joinColumn = Activity.class.getDeclaredField("createdBy")
                .getAnnotation(JoinColumn.class)
                .name();
        Index createdByIndex = Arrays.stream(Activity.class.getAnnotation(Table.class).indexes())
                .filter(index -> index.name().equals("idx_activity_created_by"))
                .findFirst()
                .orElseThrow();

        assertThat(createdByIndex.columnList()).isEqualTo(joinColumn);
    }
}
