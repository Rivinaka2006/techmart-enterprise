package lk.techmart.core.service;

import jakarta.ejb.Remote;
import java.util.concurrent.Future;

@Remote
public interface AsyncTaskService {
    Future<Boolean> generateInvoiceAsync(Integer orderId);
}
