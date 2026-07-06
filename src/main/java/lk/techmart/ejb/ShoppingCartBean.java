package lk.techmart.ejb;

import javax.ejb.Stateful;
import javax.inject.Named;

@Stateful(name = "ShoppingCartBean")
@Named("ShoppingCartBean")
public class ShoppingCartBean {
    
}
</llm-patch>

### 2. Modify the `CartSessionBean` to use the correct annotation or JNDI lookup string

<llm-patch path="src/main/java/lk/techmart/web/session/CartSessionBean.java" matcher="BeforeAfter">
Modify CartSessionBean to inject ShoppingCartBean using @EJB

Before:
