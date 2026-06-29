package com.gtublog.automation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicationOutboxEventRepository extends JpaRepository<PublicationOutboxEvent, Long> {

    List<PublicationOutboxEvent> findTop20ByDeliveryStatusOrderByAvailableAtAsc(String deliveryStatus);

    long countByDeliveryStatus(String deliveryStatus);
}
