package com.dianping.cat.report.task.alert.heartbeat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.unidal.helper.Threads.Task;
import org.unidal.lookup.annotation.Inject;
import org.unidal.tuple.Pair;

import com.dianping.cat.Cat;
import com.dianping.cat.Constants;
import com.dianping.cat.consumer.heartbeat.HeartbeatAnalyzer;
import com.dianping.cat.consumer.heartbeat.model.entity.HeartbeatReport;
import com.dianping.cat.consumer.heartbeat.model.entity.Machine;
import com.dianping.cat.consumer.heartbeat.model.entity.Period;
import com.dianping.cat.consumer.transaction.TransactionAnalyzer;
import com.dianping.cat.consumer.transaction.model.entity.TransactionReport;
import com.dianping.cat.helper.TimeHelper;
import com.dianping.cat.home.rule.entity.Condition;
import com.dianping.cat.home.rule.entity.Config;
import com.dianping.cat.message.Transaction;
import com.dianping.cat.report.page.model.spi.ModelService;
import com.dianping.cat.report.task.alert.AlertResultEntity;
import com.dianping.cat.report.task.alert.AlertType;
import com.dianping.cat.report.task.alert.BaseAlert;
import com.dianping.cat.report.task.alert.sender.AlertEntity;
import com.dianping.cat.service.ModelRequest;
import com.dianping.cat.service.ModelResponse;

public class HeartbeatAlert extends BaseAlert implements Task {

	@Inject(type = ModelService.class, value = HeartbeatAnalyzer.ID)
	private ModelService<HeartbeatReport> m_service;

	@Inject(type = ModelService.class, value = TransactionAnalyzer.ID)
	private ModelService<TransactionReport> m_transactionService;

	private static final String[] m_metrics = { "ThreadCount", "DaemonCount", "TotalStartedCount", "CatThreadCount",
	      "PiegonThreadCount", "HttpThreadCount", "NewGcCount", "OldGcCount", "MemoryFree", "HeapUsage",
	      "NoneHeapUsage", "SystemLoadAverage", "CatMessageOverflow", "CatMessageSize" };

	private void buildArray(Map<String, double[]> map, int index, String name, double value) {
		double[] array = map.get(name);
		if (array == null) {
			array = new double[60];
			map.put(name, array);
		}
		array[index] = value;
	}

	private void convertToDeltaArray(Map<String, double[]> map, String name) {
		double[] sources = map.get(name);
		double[] targets = new double[60];

		for (int i = 1; i < 60; i++) {
			if (sources[i - 1] > 0) {
				double delta = sources[i] - sources[i - 1];

				if (delta >= 0) {
					targets[i] = delta;
				}
			}
		}
		map.put(name, targets);
	}

	private double[] extract(double[] lastHourValues, double[] currentHourValues, int maxMinute, int alreadyMinute) {
		int lastLength = maxMinute - alreadyMinute - 1;
		double[] result = new double[maxMinute];

		for (int i = 0; i < lastLength; i++) {
			result[i] = lastHourValues[60 - lastLength + i];
		}
		for (int i = lastLength; i < maxMinute; i++) {
			result[i] = currentHourValues[i - lastLength];
		}
		return result;
	}

	private double[] extract(double[] values, int maxMinute, int alreadyMinute) {
		double[] result = new double[maxMinute];

		for (int i = 0; i < maxMinute; i++) {
			result[i] = values[alreadyMinute + 1 - maxMinute + i];
		}
		return result;
	}

	private Map<String, double[]> generateArgumentMap(Machine machine) {
		Map<String, double[]> map = new HashMap<String, double[]>();
		List<Period> periods = machine.getPeriods();

		for (int index = 0; index < periods.size(); index++) {
			Period period = periods.get(index);

			buildArray(map, index, "ThreadCount", period.getThreadCount());
			buildArray(map, index, "DaemonCount", period.getDaemonCount());
			buildArray(map, index, "TotalStartedCount", period.getTotalStartedCount());
			buildArray(map, index, "CatThreadCount", period.getCatThreadCount());
			buildArray(map, index, "PiegonThreadCount", period.getPigeonThreadCount());
			buildArray(map, index, "HttpThreadCount", period.getHttpThreadCount());
			buildArray(map, index, "NewGcCount", period.getNewGcCount());
			buildArray(map, index, "OldGcCount", period.getOldGcCount());
			buildArray(map, index, "MemoryFree", period.getMemoryFree());
			buildArray(map, index, "HeapUsage", period.getHeapUsage());
			buildArray(map, index, "NoneHeapUsage", period.getNoneHeapUsage());
			buildArray(map, index, "SystemLoadAverage", period.getSystemLoadAverage());
			buildArray(map, index, "CatMessageOverflow", period.getCatMessageOverflow());
			buildArray(map, index, "CatMessageSize", period.getCatMessageSize());
		}
		convertToDeltaArray(map, "TotalStartedCount");
		convertToDeltaArray(map, "NewGcCount");
		convertToDeltaArray(map, "OldGcCount");
		convertToDeltaArray(map, "CatMessageSize");
		convertToDeltaArray(map, "CatMessageOverflow");
		return map;
	}

	private HeartbeatReport generateReport(String domain, long date) {
		ModelRequest request = new ModelRequest(domain, date)//
		      .setProperty("ip", Constants.ALL);

		if (m_service.isEligable(request)) {
			ModelResponse<HeartbeatReport> response = m_service.invoke(request);

			return response.getModel();
		} else {
			throw new RuntimeException("Internal error: no eligable ip service registered for " + request + "!");
		}
	}

	@Override
	public String getName() {
		return AlertType.HeartBeat.getName();
	}

	private void processDomain(String domain) {
		List<Config> configs = m_ruleConfigManager.queryConfigsByGroup(domain);
		int minute = getAlreadyMinute();
		int maxMinute = queryCheckMinuteAndConditions(configs).getKey();

		if (minute >= maxMinute - 1) {
			long currentMill = System.currentTimeMillis();
			long currentHourMill = currentMill - currentMill % TimeHelper.ONE_HOUR;
			HeartbeatReport currentReport = generateReport(domain, currentHourMill);

			for (Machine machine : currentReport.getMachines().values()) {
				String ip = machine.getIp();
				Map<String, double[]> arguments = generateArgumentMap(machine);

				for (String metric : m_metrics) {
					double[] values = extract(arguments.get(metric), maxMinute, minute);

					processMeitrc(domain, ip, metric, values);
				}
			}
		} else if (minute < 0) {
			long currentMill = System.currentTimeMillis();
			long lastHourMill = currentMill - currentMill % TimeHelper.ONE_HOUR - TimeHelper.ONE_HOUR;
			HeartbeatReport lastReport = generateReport(domain, lastHourMill);

			for (Machine machine : lastReport.getMachines().values()) {
				String ip = machine.getIp();
				Map<String, double[]> arguments = generateArgumentMap(machine);

				for (String metric : m_metrics) {
					double[] values = extract(arguments.get(metric), maxMinute, 59);

					processMeitrc(domain, ip, metric, values);
				}
			}
		} else {
			long currentMill = System.currentTimeMillis();
			long currentHourMill = currentMill - currentMill % TimeHelper.ONE_HOUR;
			long lastHourMill = currentHourMill - TimeHelper.ONE_HOUR;
			HeartbeatReport currentReport = generateReport(domain, currentHourMill);
			HeartbeatReport lastReport = generateReport(domain, lastHourMill);

			for (Machine lastMachine : lastReport.getMachines().values()) {
				String ip = lastMachine.getIp();
				Machine currentMachine = currentReport.getMachines().get(ip);

				if (currentMachine != null) {
					Map<String, double[]> lastHourArguments = generateArgumentMap(lastMachine);
					Map<String, double[]> currentHourArguments = generateArgumentMap(currentMachine);

					for (String metric : m_metrics) {
						double[] values = extract(lastHourArguments.get(metric), currentHourArguments.get(metric), maxMinute,
						      minute);

						processMeitrc(domain, ip, metric, values);
					}
				}
			}
		}
	}

	private void processMeitrc(String domain, String ip, String metric, double[] values) {
		try {
			List<Config> configs = m_ruleConfigManager.queryConfigs(domain, metric);
			Pair<Integer, List<Condition>> resultPair = queryCheckMinuteAndConditions(configs);
			int maxMinute = resultPair.getKey();
			List<Condition> conditions = resultPair.getValue();
			double[] baseline = new double[maxMinute];
			List<AlertResultEntity> alerts = m_dataChecker.checkData(values, baseline, conditions);

			for (AlertResultEntity alertResult : alerts) {
				AlertEntity entity = new AlertEntity();

				entity.setDate(alertResult.getAlertTime()).setContent(alertResult.getContent())
				      .setLevel(alertResult.getAlertLevel());
				entity.setMetric(metric).setType(getName()).setGroup(domain);
				entity.getParas().put("ip", ip);
				m_sendManager.addAlert(entity);
			}
		} catch (Exception e) {
			Cat.logError(e);
		}
	}

	private Set<String> queryDomains() {
		Set<String> domains = new HashSet<String>();
		ModelRequest request = new ModelRequest("cat", System.currentTimeMillis());

		if (m_transactionService.isEligable(request)) {
			ModelResponse<TransactionReport> response = m_transactionService.invoke(request);
			domains.addAll(response.getModel().getDomainNames());
		}
		return domains;
	}

	@Override
	public void run() {
		boolean active = true;
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			active = false;
		}

		while (active) {
			Transaction t = Cat.newTransaction("AlertHeartbeat", TimeHelper.getMinuteStr());
			long current = System.currentTimeMillis();

			try {
				Set<String> domains = queryDomains();

				for (String domain : domains) {
					try {
						processDomain(domain);
					} catch (Exception e) {
						Cat.logError(e);
					}
				}

				t.setStatus(Transaction.SUCCESS);
			} catch (Exception e) {
				t.setStatus(e);
			} finally {
				t.complete();
			}
			long duration = System.currentTimeMillis() - current;

			try {
				if (duration < DURATION) {
					Thread.sleep(DURATION - duration);
				}
			} catch (InterruptedException e) {
				active = false;
			}
		}
	}

	@Override
	public void shutdown() {
	}

}
