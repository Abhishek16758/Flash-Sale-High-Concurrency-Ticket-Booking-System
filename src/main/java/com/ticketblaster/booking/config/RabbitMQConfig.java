package com.ticketblaster.booking.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String QUEUE_BOOKING = "ticket.booking.queue";
    public static final String EXCHANGE_BOOKING = "ticket.booking.exchange";
    public static final String ROUTING_KEY_BOOKING = "ticket.booking.routingKey";

    @Bean
    public Queue bookingQueue() {
        return QueueBuilder.durable(QUEUE_BOOKING).build();
    }

    @Bean
    public DirectExchange bookingExchange() {
        return new DirectExchange(EXCHANGE_BOOKING);
    }

    @Bean
    public Binding bookingBinding(Queue bookingQueue, DirectExchange bookingExchange) {
        return BindingBuilder.bind(bookingQueue).to(bookingExchange).with(ROUTING_KEY_BOOKING);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public AmqpTemplate amqpTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }
}