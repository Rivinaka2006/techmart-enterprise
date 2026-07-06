package lk.techmart.ejb.service;

import jakarta.ejb.AsyncResult;
import jakarta.ejb.Asynchronous;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lk.techmart.core.entity.AsyncTask;
import lk.techmart.core.service.AsyncTaskService;
import lk.techmart.core.service.SystemHealthService;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.Future;

@Slf4j
@Stateless
public class AsyncTaskServiceImpl implements AsyncTaskService {

    @PersistenceContext(unitName = "TechMartPU")
    private EntityManager em;

    @EJB
    private SystemHealthService healthService;

    @Override
    @Asynchronous 
    public Future<Boolean> generateInvoiceAsync(Integer orderId) {
        healthService.incrementAsyncTask();

        AsyncTask task = new AsyncTask();
        task.setTaskName("INVOICE_GENERATION_ORDER_" + orderId);
        task.setStatus("RUNNING");
        task.setStartedAt(LocalDateTime.now());
        em.persist(task);

        boolean success = true;
        try {
            
            Thread.sleep(150 + new Random().nextInt(350));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            success = false;
        } catch (Exception e) {
            log.error("Invoice generation failed for order {}", orderId, e);
            success = false;
        }

        task.setStatus(success ? "COMPLETED" : "FAILED");
        task.setCompletedAt(LocalDateTime.now());

        return new AsyncResult<>(success);
    }
}
