package com.campuspulse.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

public final class Api {
    private Api() {}
    public static String localize(String chinese, String english) {
        return LocaleContextHolder.getLocale().getLanguage().equals("zh") ? chinese : english;
    }
    private static final Map<String, String> ENGLISH = Map.ofEntries(
        Map.entry("不支持的行为或参数过长", "Unsupported event type or parameters are too long."),
        Map.entry("不能停用当前管理员账号", "You cannot disable your current administrator account."),
        Map.entry("不能移除最后一位有效管理员", "At least one active administrator must remain."),
        Map.entry("不能给自己发送私信", "You cannot send a direct message to yourself."),
        Map.entry("专业长度不超过 128", "Major must be at most 128 characters."),
        Map.entry("个人简介长度不超过 300", "Bio must be at most 300 characters."),
        Map.entry("为保障安全，请使用验证码修改密码", "Use an email verification code to change your password."),
        Map.entry("人数上限不能低于当前已通过人数", "Capacity cannot be lower than the approved participant count."),
        Map.entry("仅队伍成员可查看或发送消息", "Only active team members can read or send messages."),
        Map.entry("入队申请不存在", "Team application not found."),
        Map.entry("关键词过长", "Search keywords are too long."),
        Map.entry("创建失败", "Creation failed. Please try again."),
        Map.entry("名额已满", "No places remain."),
        Map.entry("地点不能为空且长度不超过 200", "Enter a location of at most 200 characters."),
        Map.entry("学历选项不支持", "Choose a supported education level."),
        Map.entry("学院不能为空且长度不超过 128", "Enter a college of at most 128 characters."),
        Map.entry("学院长度不超过 128", "College must be at most 128 characters."),
        Map.entry("实时连接凭证无效或已过期", "The stream ticket is invalid or expired. Request a new ticket."),
        Map.entry("实时频道参数错误", "Invalid chat subscription parameters."),
        Map.entry("审核状态不支持", "Unsupported review status."),
        Map.entry("对方用户不存在", "The other user was not found."),
        Map.entry("封面地址过长", "The cover image URL is too long."),
        Map.entry("已归档或结束的活动不可修改", "Archived or ended activities cannot be edited."),
        Map.entry("开始时间不能为空", "Enter a start time."),
        Map.entry("当前报名状态不可改为拒绝", "The registration can no longer be rejected in its current state."),
        Map.entry("当前报名状态不可改为通过", "The registration can no longer be approved in its current state."),
        Map.entry("当前申请状态不可改为拒绝", "The application can no longer be rejected in its current state."),
        Map.entry("当前申请状态不可改为通过", "The application can no longer be approved in its current state."),
        Map.entry("当前账号未绑定邮箱", "This account does not have a verified email address."),
        Map.entry("您已在队伍中", "You are already a member of this team."),
        Map.entry("您已报名该活动", "You already registered for this activity."),
        Map.entry("手机号不能为空且长度不超过 32", "Enter a phone number of at most 32 characters."),
        Map.entry("报名后可查看发布人联系方式", "Register for the activity to view organizer contact details."),
        Map.entry("报名状态已变化", "The registration status changed. Refresh and try again."),
        Map.entry("报名记录不存在", "Registration not found."),
        Map.entry("报名通过后才可进入活动群聊", "Your registration must be approved to access activity chat."),
        Map.entry("推荐权重必须在 0 至 1000 之间", "Recommendation weight must be between 0 and 1000."),
        Map.entry("搜索类型不支持", "Unsupported search type."),
        Map.entry("操作原因过长", "The reason is too long."),
        Map.entry("文件不存在", "File not found."),
        Map.entry("新队长必须为有效队员", "The new captain must be an active team member."),
        Map.entry("无权限查看报名信息", "You do not have permission to view registrations."),
        Map.entry("无权限查看申请列表", "You do not have permission to view applications."),
        Map.entry("无权限管理该活动", "You do not have permission to manage this activity."),
        Map.entry("无权限订阅该聊天频道", "You do not have permission to subscribe to this chat."),
        Map.entry("最大人数不合法", "Invalid capacity."),
        Map.entry("未找到可取消的入队申请", "No cancellable team application was found."),
        Map.entry("未找到可取消的报名记录", "No cancellable registration was found."),
        Map.entry("标签不合法", "Invalid tag."),
        Map.entry("标签不存在", "Tag not found."),
        Map.entry("标签不能超过 20 个", "Choose at most 20 tags."),
        Map.entry("标签仍在使用，请先移除关联", "The tag is still in use. Remove its associations first."),
        Map.entry("标签名不合法", "Invalid tag name."),
        Map.entry("标签类型不支持", "Unsupported tag type."),
        Map.entry("标题不能为空且长度不超过 200", "Enter a title of at most 200 characters."),
        Map.entry("校区选项不支持", "Choose a supported campus."),
        Map.entry("此消息标识已被其他内容使用", "That message identifier has already been used with different content."),
        Map.entry("活动不存在", "Activity not found."),
        Map.entry("活动介绍过长", "The activity description is too long."),
        Map.entry("活动名额已满", "The activity is full."),
        Map.entry("活动尚未开放组队", "This activity is not open for team formation."),
        Map.entry("活动已更新，请刷新后重试", "The activity changed. Refresh and try again."),
        Map.entry("活动已结束、已归档或尚未通过审核", "The activity has ended, is archived or has not been approved."),
        Map.entry("活动标签至少选择 1 个", "Choose at least one activity tag."),
        Map.entry("活动状态不支持", "Unsupported activity status."),
        Map.entry("消息内容不能为空且长度不超过 1000", "Enter a message of at most 1000 characters."),
        Map.entry("消息幂等标识不合法", "Invalid message request identifier."),
        Map.entry("消息游标不合法", "Invalid message cursor."),
        Map.entry("用户不存在", "User not found."),
        Map.entry("用途不支持", "Unsupported verification purpose."),
        Map.entry("申请状态已变化", "The application status changed. Refresh and try again."),
        Map.entry("申请说明不能超过 1000 字", "Application message must be at most 1000 characters."),
        Map.entry("目标不存在", "The requested item was not found."),
        Map.entry("真实姓名不能为空且长度不超过 64", "Enter a real name of at most 64 characters."),
        Map.entry("结束时间不能早于开始时间", "End time cannot be before start time."),
        Map.entry("群聊未开启", "Group chat is not enabled."),
        Map.entry("自我介绍过长", "The introduction is too long."),
        Map.entry("角色不支持", "Unsupported role."),
        Map.entry("该账号已绑定邮箱，可使用换绑功能", "This account already has a verified email address. Use Change email."),
        Map.entry("请先转让队长身份", "Transfer leadership before leaving the team."),
        Map.entry("请输入要咨询的问题", "Enter your support question."),
        Map.entry("请选择新队长", "Choose a new team captain."),
        Map.entry("通知筛选条件不支持", "Unsupported notification filter."),
        Map.entry("邮箱不能为空", "Enter an email address."),
        Map.entry("邮箱已变更，请重试", "Your email address changed. Refresh and try again."),
        Map.entry("问题长度不能超过 500 字", "Your question must be at most 500 characters."),
        Map.entry("队伍不存在", "Team not found."),
        Map.entry("队伍人数上限不能低于当前成员数", "Team capacity cannot be lower than the active member count."),
        Map.entry("队伍人数上限不能超过 50", "Team capacity cannot exceed 50."),
        Map.entry("队伍人数不能为负数", "Team member count cannot be negative."),
        Map.entry("队伍人数至少为 2", "Team capacity must be at least 2."),
        Map.entry("队伍名称不能为空且长度不超过 200", "Enter a team name of at most 200 characters."),
        Map.entry("队伍已关闭或结束", "This team is closed or has ended."),
        Map.entry("队伍已更新，请刷新后重试", "The team changed. Refresh and try again."),
        Map.entry("队伍已满员", "The team is full."),
        Map.entry("队伍简介长度不超过 800", "Team description must be at most 800 characters."),
        Map.entry("队长请先转让身份或关闭队伍", "Transfer leadership or close the team before leaving."),
        Map.entry("需要管理员权限", "Administrator access is required."),
        Map.entry("未登录或登录已过期", "Please sign in again."),
        Map.entry("无权限", "You do not have permission to perform this action."),
        Map.entry("参数错误", "Invalid request parameters."),
        Map.entry("账号已被禁用", "This account is disabled."),
        Map.entry("账号或密码错误", "Incorrect account or password."),
        Map.entry("账号或密码错误次数过多，请稍后再试", "Too many failed login attempts. Please try again later."),
        Map.entry("邮箱格式不正确", "Enter a valid email address."),
        Map.entry("验证码错误", "Incorrect verification code."),
        Map.entry("验证码已过期或不存在", "The verification code has expired or does not exist."),
        Map.entry("验证码已使用或过期，请重新获取", "The verification code has been used or expired. Request a new code."),
        Map.entry("验证码错误次数过多，请重新获取", "Too many incorrect attempts. Request a new code."),
        Map.entry("操作过于频繁，请稍后再试", "Too many requests. Please try again later."),
        Map.entry("邮件发送失败，请稍后重试", "Email delivery failed. Please try again later."),
        Map.entry("用户名已存在", "That username is already in use."),
        Map.entry("学号已被占用", "That student number is already in use."),
        Map.entry("邮箱已被绑定", "That email address is already linked to an account."),
        Map.entry("该邮箱已被绑定", "That email address is already linked to an account."),
        Map.entry("该邮箱未绑定账号", "No verified account uses that email address."),
        Map.entry("请先绑定邮箱", "Please verify an email address first."),
        Map.entry("密码需包含大小写字母、数字和特殊字符", "Password must include uppercase and lowercase letters, a number and a symbol."),
        Map.entry("请使用邮箱验证码注册接口 /api/auth/register-email", "Use email-verified registration at /api/auth/register-email."),
        Map.entry("图片超过大小限制", "The image exceeds the upload size limit."),
        Map.entry("文件内容不是有效图片", "The file is not a supported image."),
        Map.entry("文件为空", "Choose a nonempty file."),
        Map.entry("系统繁忙，请稍后再试", "The service is temporarily unavailable. Please try again later.")
    );
    public static String message(String value, HttpStatus status) {
        if (LocaleContextHolder.getLocale().getLanguage().equals("zh") || value.codePoints().noneMatch(cp -> cp > 0x2e80 && cp < 0xa000)) return value;
        if (value.startsWith("标签不存在：")) return "Tag not found: " + value.substring("标签不存在：".length());
        var fields = Map.ofEntries(Map.entry("用户名", "Username"), Map.entry("密码", "Password"), Map.entry("新密码", "New password"), Map.entry("姓名", "Name"), Map.entry("学号", "Student number"), Map.entry("邮箱", "Email"), Map.entry("新邮箱", "New email"), Map.entry("账号", "Account"), Map.entry("用途", "Purpose"), Map.entry("原邮箱验证码", "Current email verification code"), Map.entry("新邮箱验证码", "New email verification code"));
        for (var field : fields.entrySet()) {
            if (value.equals(field.getKey() + "不能为空")) return field.getValue() + " is required.";
            if (value.startsWith(field.getKey() + "长度不能少于 ")) return field.getValue() + " must contain at least " + value.substring((field.getKey() + "长度不能少于 ").length()) + " characters.";
            if (value.startsWith(field.getKey() + "长度不能超过 ")) return field.getValue() + " must contain at most " + value.substring((field.getKey() + "长度不能超过 ").length()) + " characters.";
        }
        return ENGLISH.getOrDefault(value, switch (status) {
            case BAD_REQUEST -> "The request is invalid. Check the entered values.";
            case UNAUTHORIZED -> "Please sign in again.";
            case FORBIDDEN -> "You do not have permission to perform this action.";
            case NOT_FOUND -> "The requested record was not found.";
            case CONFLICT -> "The record changed or already exists. Refresh and try again.";
            case TOO_MANY_REQUESTS -> "Too many requests. Please try again later.";
            default -> "The operation could not be completed. Please try again.";
        });
    }
    public static <T> ApiResponse<T> ok(T data) { return new ApiResponse<>(true, "OK", data); }
    public static ApiResponse<Void> ok() { return ok(null); }
    public static ApiResponse<Void> fail(String message) { return failure(message, HttpStatus.BAD_REQUEST); }
    public static ApiResponse<Void> failure(String value, HttpStatus status) { return new ApiResponse<>(false, message(value, status), null, status.name()); }
    public record ApiResponse<T>(boolean success, String message, T data, String code) {
        public ApiResponse(boolean success, String message, T data) { this(success, message, data, success ? "OK" : "REQUEST_FAILED"); }
    }
    public static final class ApiException extends RuntimeException {
        private final HttpStatus status;
        public ApiException(HttpStatus status, String message) { super(message); this.status = status; }
        public HttpStatus status() { return status; }
    }
    @RestControllerAdvice
    public static final class GlobalExceptionHandler {
        private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
        @ExceptionHandler(ApiException.class)
        public ResponseEntity<ApiResponse<Void>> handleApi(ApiException e) { return ResponseEntity.status(e.status()).body(failure(e.getMessage(), e.status())); }
        @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class, HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
        public ResponseEntity<ApiResponse<Void>> validation(Exception e) { return ResponseEntity.badRequest().body(failure("参数错误", HttpStatus.BAD_REQUEST)); }
        @ExceptionHandler(MaxUploadSizeExceededException.class)
        public ResponseEntity<ApiResponse<Void>> upload(MaxUploadSizeExceededException e) { return ResponseEntity.status(413).body(failure("图片超过大小限制", HttpStatus.PAYLOAD_TOO_LARGE)); }
        @ExceptionHandler(DataIntegrityViolationException.class)
        public ResponseEntity<ApiResponse<Void>> conflict(DataIntegrityViolationException e) { return ResponseEntity.status(409).body(failure(localize("记录冲突，请刷新后重试", "The record conflicts with an existing record. Refresh and try again."), HttpStatus.CONFLICT)); }
        @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class,
                HttpRequestMethodNotSupportedException.class, HttpMediaTypeNotSupportedException.class,
                HttpMediaTypeNotAcceptableException.class, ServletRequestBindingException.class,
                MissingServletRequestPartException.class, ErrorResponseException.class})
        public ResponseEntity<ApiResponse<Void>> framework(Exception e) {
            ErrorResponse error = (ErrorResponse) e;
            HttpStatus status = HttpStatus.resolve(error.getStatusCode().value());
            if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
            String message = switch (status) {
                case NOT_FOUND -> localize("请求的资源不存在", "The requested resource was not found.");
                case METHOD_NOT_ALLOWED -> localize("此资源不支持该请求方法", "This request method is not supported for the resource.");
                case UNSUPPORTED_MEDIA_TYPE -> localize("不支持的请求内容类型", "The request content type is not supported.");
                case NOT_ACCEPTABLE -> localize("不支持的响应内容类型", "The requested response content type is not supported.");
                default -> status.is4xxClientError() ? localize("参数错误", "Invalid request parameters.")
                        : localize("系统繁忙，请稍后再试", "The service is temporarily unavailable. Please try again later.");
            };
            // Preserve protocol headers such as Allow, without exposing paths or parser details.
            return ResponseEntity.status(status).headers(error.getHeaders()).body(failure(message, status));
        }
        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiResponse<Void>> unknown(Exception e, HttpServletRequest request) {
            log.error("Unhandled exception at {}", request.getRequestURI(), e);
            return ResponseEntity.internalServerError().body(failure("系统繁忙，请稍后再试", HttpStatus.INTERNAL_SERVER_ERROR));
        }
    }
}
