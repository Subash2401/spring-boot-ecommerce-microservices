package com.subash.notificationservice.service;

import org.springframework.web.util.HtmlUtils;

import com.subash.notificationservice.model.Notification;

// Builds the subject and the HTML body of a notification email.
// Kept as plain Java (no template engine): one subject per notification type,
// and one small HTML layout around the notification's message.
public final class EmailContent {

	private EmailContent() {
		// only static methods
	}

	// One subject line per notification type, so the user can tell the emails apart in the inbox.
	public static String subject(Notification notification) {
		String order = "Order #" + notification.getOrderId();
		return switch (notification.getType()) {
		case "AWAITING_PAYMENT" -> order + ": please complete your payment";
		case "ORDER_PLACED" -> order + " is confirmed";
		case "PAYMENT_FAILED" -> order + ": payment failed";
		case "OUT_OF_STOCK" -> order + " was cancelled (out of stock)";
		case "ORDER_CANCELLED" -> order + " was cancelled";
		case "PAYMENT_REFUNDED" -> order + ": your payment was refunded";
		default -> order + ": update";
		};
	}

	// A simple HTML email. The message is escaped (HtmlUtils.htmlEscape), so no text
	// from an event can ever become HTML. Inline styles only: many mail apps ignore <style>.
	public static String html(Notification notification) {
		String title = HtmlUtils.htmlEscape(subject(notification));
		String message = HtmlUtils.htmlEscape(notification.getMessage());
		return """
				<!DOCTYPE html>
				<html>
				<body style="margin:0;padding:24px;background:#f4f4f5;font-family:Arial,sans-serif;color:#222;">
				  <div style="max-width:520px;margin:0 auto;background:#ffffff;border-radius:8px;padding:24px;">
				    <h2 style="margin:0 0 16px;font-size:18px;color:#212529;">Microshop</h2>
				    <h3 style="margin:0 0 12px;font-size:16px;">%s</h3>
				    <p style="margin:0 0 12px;font-size:14px;line-height:1.5;">Hello,</p>
				    <p style="margin:0 0 24px;font-size:14px;line-height:1.5;">%s</p>
				    <p style="margin:0;font-size:12px;color:#6c757d;">You get this email because you placed an order at Microshop.</p>
				  </div>
				</body>
				</html>
				""".formatted(title, message);
	}

	// The same email as plain text, for mail apps that do not show HTML.
	public static String text(Notification notification) {
		return "Hello,\n\n" + notification.getMessage() + "\n\n-- Microshop";
	}
}
