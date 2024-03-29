package com.example.log.aop;


import com.alibaba.fastjson.JSON;
import com.example.log.annotation.LoggerAnnotation;
import com.example.log.pojo.Log;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

@Aspect             //@Aspect是将该类声明为切面类
@Component          //@component把该类实例化放入到spring容器中，
public class WebLogAspect {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    //@Pointcut：在使用其他注解时，需要写包的表达式，而@Pointcut就是用来声明一样的包路径
    @Pointcut("@annotation(com.example.log.annotation.LoggerAnnotation)")
    public void LoggerAnnotationCut(){}

    @Around(value = "LoggerAnnotationCut()")  ///先进@Around 再进@Before 再进@After
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable{
        StringBuffer sign = new StringBuffer("详情：");

        //把参数拼接到详情里。
        for (int i = 0; i < joinPoint.getArgs().length; i++) {
            sign = sign.append(joinPoint.getArgs()[i]).append("; ");
        }

        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature(); //获取methodSignature方法签名
        LoggerAnnotation annotation = methodSignature.getMethod().getAnnotation(LoggerAnnotation.class); //从methodSignature方法签名 获取方法，再获取方法上的注解

        Object proceed = null;
        if (annotation != null) {
            proceed = joinPoint.proceed();

            Log log = new Log();
            log.setModel(annotation.modelName().getModelName()); //获取对应 模块名称
            log.setMethod(annotation.modelName().getMethodName());  //获取对应 方法名称
            log.setParams((sign.toString())); //传过来的一串参数。
            log.setCreateDate(new Date()); //时间是现在。
            log.setCreateMan("CL"); //这里操作人直接写死。这里简化了，其实可以从Session里获得操作人名字。

            //三个不同的convertAndSend，推荐用第二个持久化的消息。

//            //1.
//            //消息 没有持久化的convertAndSend
//            //第一个参数是 交换器名称。第二个参数是 路由key。第三个参数是要传递的对象。
//            //Routing（路由模式）：有选择的接收消息。用过这个。用direct交换机，有routingKey。消息发到交换机，再根据 路由key 发到对应队列。
//            rabbitTemplate.convertAndSend("log.test.exchange", "log.test.routing.key", log);


            //2.
            //消息 持久化的convertAndSend。直接传log 不用把log序列化放到message里。这样也不用导阿里巴巴的fastjson依赖。
            MessagePostProcessor processor = message -> {
                //消息持久化设置（取值有两个 NON_PERSISTENT=1，PERSISTENT=2）
                message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                return message;
            };
            rabbitTemplate.convertAndSend("log.test.exchange", "log.test.routing.key", log, processor);


//            //3.
//            //消息 持久化的convertAndSend。log放在Message里的。这样log对象需要转为string还要再转为bytes。也要导个阿里巴巴的fastjson依赖。
//            Message message = MessageBuilder.withBody(JSON.toJSONString(log).getBytes())
//                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
//                    .build();
//            MessageProperties messageProperties = message.getMessageProperties();
//            messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
//            rabbitTemplate.convertAndSend("log.test.exchange", "log.test.routing.key", message);


            System.out.println("发送消息=" + log.toString());
        }
        return proceed;
    }
}
