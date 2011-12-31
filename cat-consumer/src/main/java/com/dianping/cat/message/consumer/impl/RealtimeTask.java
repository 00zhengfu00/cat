package com.dianping.cat.message.consumer.impl;

import org.apache.log4j.Logger;

import com.dianping.cat.message.consumer.failure.FailureReportMessageAnalyzer;
import com.dianping.cat.message.spi.MessageAnalyzer;
import com.dianping.cat.message.spi.MessageConsumer;
import com.dianping.cat.message.spi.MessageQueue;
import com.dianping.cat.message.spi.MessageTree;
import com.site.lookup.annotation.Inject;

/**
 * The consumer is used to record the error and exception state of the system��
 * 
 * The consumer records the lasteast info of one hour��
 * 
 * �������⣺ 1��Consumer��֧��domain���в�ֲ����̨������� 2������Ҫ�����ڴ����ݵ��ȶ��ԣ�������ÿ��Сʱ��һ�ݡ�
 * 3����Ҫ����Consumer����Ӱ�쵽�����ĳ������һ��Message��Ҫ�������ء�
 * 4����Ҫ���Ǻ�������Consumer���߳��ȶ��ԣ�����ʹ��һ���̴߳���һ��ʱ���ڵĶ������ҷ��ء�
 * duration��Queue���Ծ�����Ϊ3�����ͣ���һ�����У����ڴ���Ķ��У���һ�����У����ǿ���ѭ�����á�
 * 6���ڴ���һ��Сʱ�����ݴ�����⣬�ṩService�������ʡ� 7�������̸߳��±����ʱ��KEY��Ϣ��
 * 
 * @author yong.you
 * 
 */
public class RealtimeTask {	

	private static Logger logger = Logger.getLogger(RealtimeTask.class);
	private RealtimeConsumerConfig m_config;
	private DefaultMessageQueue m_firstQueue;
	private DefaultMessageQueue m_secondQueue;
	
	//������ҵ���Լ�ʵ�ֵ�MessageAnalyzer��Class�ࡣ
	@Inject 
	private String className; 
	
	public RealtimeTask(RealtimeConsumerConfig config) {
		long currentTimeMillis = System.currentTimeMillis();
		long lastHour = currentTimeMillis - currentTimeMillis% m_config.getQueueTime();
		m_firstQueue = new DefaultMessageQueue(m_config.getDuration(), lastHour);
		m_secondQueue = new DefaultMessageQueue(m_config.getDuration(), lastHour+m_config.getQueueTime());
		startThread(m_firstQueue);
		startThread(m_secondQueue);
	}

	/**
	 * ��MessageTree����Queue�С� ����һ��СʱMessageTree������һ��Queue��newһ��Queue��newһ��Thread��
	 */
	public void consume(MessageTree tree) {
		if(m_firstQueue.isExpired()){
			switchQueue();
			consume(tree);
		}
		else{
			if(m_firstQueue.inRange(tree)){
				m_firstQueue.offer(tree);
			}
			else if(m_secondQueue.inRange(tree)){
				m_secondQueue.offer(tree);
			}
			else {
				logger.error("Discard it "+ tree);		
			}
		}
	}

	public void switchQueue(){
		long secondQueueTime = m_secondQueue.getStart();
		m_firstQueue  = m_secondQueue;
		m_secondQueue = new DefaultMessageQueue(m_config.getDuration(), secondQueueTime+m_config.getQueueTime());	
		startThread(m_secondQueue);
	}
	
	private void startThread(final MessageQueue queue){
		// ���ݴ�������ClassName����һ��ʵ��
		final FailureReportMessageAnalyzer analyzer = new FailureReportMessageAnalyzer();
		Thread thread=new Thread(new Runnable() {
			@Override
			public void run() {
				analyzer.analyze(queue);				
			}
		});
		thread.start();
	}
	
	
}
