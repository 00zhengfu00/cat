����ע�ⷽʽ��� ʵ��������ע�ⷽʽ���ٶ�ϵͳ������㣬����Ҫ������򷽷�����ע�⼴�ɣ���������Ӱ�죩 �����������: 1,web.xml������filter,url-pattern������Ҫ����restful�ӿ�,��ֹ��̬��Դ���� cat-filter com.dianping.cat.servlet.CatFilter cat-filter /test1/ /test2/ REQUEST FORWARD

2,CatCacheTransactionע��ʾ��

@CatCacheTransaction
public V get(K key) {

}
@CatCacheTransaction
public void put(K key, V value) {

}
@CatCacheTransaction
public void delete(K key) {  

}
3,CatHttpRequestTransactionע��ʾ��,URL�ۺ�ע��

@RequestMapping(value = "/orders/{userId}/{orderStatus}")
@ResponseBody
@CatHttpRequestTransaction(type = "URL", name = "/orders")
public String userOrders() {

}
4,CatDubboClientTransactionע��ʾ��

@CatDubboClientTransaction(callApp="orders",callServer = "orderServer")
public List<Long> getOrdersByUser() {

}