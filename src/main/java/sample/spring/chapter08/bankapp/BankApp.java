package sample.spring.chapter08.bankapp;

import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.broker.BrokerService;
import org.apache.log4j.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import sample.spring.chapter08.bankapp.domain.BankAccountDetails;
import sample.spring.chapter08.bankapp.domain.FixedDepositDetails;
import sample.spring.chapter08.bankapp.service.BankAccountService;
import sample.spring.chapter08.bankapp.service.FixedDepositService;

public class BankApp {
	private static Logger logger = Logger.getLogger(BankApp.class);

	public static void main(String args[]) throws Exception {
		ApplicationContext context = new ClassPathXmlApplicationContext(
				"classpath:META-INF/spring/applicationContext.xml");
		// Part 1: set up a new bank account
		BankAccountService bankAccountService = context
				.getBean(BankAccountService.class);
		BankAccountDetails bankAccountDetails = new BankAccountDetails();
		bankAccountDetails.setBalanceAmount(1000);
		bankAccountDetails.setLastTransactionTimestamp(new Date());
		
		int bankAccountId = bankAccountService
				.createBankAccount(bankAccountDetails);

		logger.info("Created bank account with id - " + bankAccountId);

		// Part 2: set up fix deposit
		FixedDepositService fixedDepositService = context
				.getBean(FixedDepositService.class);
		FixedDepositDetails fdd = new FixedDepositDetails();
		fdd.setActive("N");
		fdd.setBankAccountId(bankAccountId);
		fdd.setFdCreationDate(new Date());
		fdd.setFdAmount(500);
		fdd.setTenure(12);
		fdd.setEmail("someemail@somedomain.com");
		
		// createFixedDeposit() sends 2 JMS messages:
		// one is to send email.
		// one is to save deposit data to database.
		// Also, this method is @CacheEvict: remove all data in "FixedDepositList" cache
		fixedDepositService.createFixedDeposit(fdd);

		Thread.sleep(5000);
		
		// Part 3: get deposit data from database
		
		// findFixedDepositsByBankAccount() is @Cacheable: 
		// method executes only when the value is not in the cache.
		// returned value is stored in "FixedDepositList" cache.
		
		// 1st call returns value from method execution
		fixedDepositService.findFixedDepositsByBankAccount(bankAccountId);
		// 2nd call returns value from cache
		fixedDepositService.findFixedDepositsByBankAccount(bankAccountId);
		
		// Part 4: make a new fix deposit
		
		// createFixedDeposit() sends 2 JMS msgs.
		// @CacheEvict: remove the previous deposit data from cache, then make new deposit
		fixedDepositService.createFixedDeposit(fdd);

		Thread.sleep(5000);
		
		// Part 5: get deposit data from database
		
		// findFixedDepositsByBankAccount() is @Cacheable:
		// Now cache is empty. So get value from method execution, store into cache.
		List<FixedDepositDetails> fixedDepositDetailsList = fixedDepositService
				.findFixedDepositsByBankAccount(bankAccountId);

		
		for (FixedDepositDetails detail : fixedDepositDetailsList) {
			
			// getFixedDeposit() is @CachePut:
			// We always get fresh data from database, instead of cache.
			fixedDepositService.getFixedDeposit(detail.getFixedDepositId());
		}

		for (FixedDepositDetails detail : fixedDepositDetailsList) {
			
			// getFixedDepositFromCache() is @Cacheable:
			// we first try to get data from cache. If data is not in cache, get it from method execution.
			fixedDepositService.getFixedDepositFromCache(detail
					.getFixedDepositId());
		}

		Thread.sleep(5000);

		Map<String, DefaultMessageListenerContainer> containers = context
				.getBeansOfType(DefaultMessageListenerContainer.class);
		Set<String> keySet = containers.keySet();
		Iterator<String> iterator = keySet.iterator();

		while (iterator.hasNext()) {
			DefaultMessageListenerContainer container = containers.get(iterator
					.next());
			System.out.println("Container - " + container);
			container.destroy();
		}

		ThreadPoolTaskScheduler poolTaskScheduler = (ThreadPoolTaskScheduler) context
				.getBean("emailScheduler");
		poolTaskScheduler.destroy();
		BrokerService brokerService = context.getBean(BrokerService.class);
		brokerService.stop();
	}
}