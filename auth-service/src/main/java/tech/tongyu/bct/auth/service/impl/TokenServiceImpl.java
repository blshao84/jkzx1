package tech.tongyu.bct.auth.service.impl;

import com.google.common.collect.Lists;
import io.jsonwebtoken.Claims;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tongyu.bct.acl.common.UserStatus;
import tech.tongyu.bct.acl.utils.TokenConstants;
import tech.tongyu.bct.acl.utils.TokenUtils;
import tech.tongyu.bct.auth.AuthConstants;
import tech.tongyu.bct.auth.config.ExternalConfig;
import tech.tongyu.bct.auth.dao.IpTokenBindRepo;
import tech.tongyu.bct.auth.dao.IpUserBindRepo;
import tech.tongyu.bct.auth.dao.entity.IpTokenBindDbo;
import tech.tongyu.bct.auth.dto.UserDTO;
import tech.tongyu.bct.auth.enums.UserTypeEnum;
import tech.tongyu.bct.auth.manager.UserManager;
import tech.tongyu.bct.auth.service.TokenService;
import tech.tongyu.bct.auth.utils.CommonUtils;
import tech.tongyu.bct.common.exception.CustomException;
import tech.tongyu.bct.common.util.JsonUtils;
import tech.tongyu.bct.common.util.SM4Utils;
import tech.tongyu.bct.common.util.SystemConfig;
import tech.tongyu.bct.common.util.TimeUtils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class TokenServiceImpl implements TokenService {

    @Value("${settings.issuer}")
    private String issuer;

    @Value("${settings.secret}")
    private String secret;

    @Value("${settings.refreshSecret}")
    private String refreshSecret;

    @Autowired
    private ExternalConfig externalConfig;

    private UserManager userManager;

    private IpTokenBindRepo ipTokenBindRepo;

    private IpUserBindRepo ipUserBindRepo;

    @Autowired
    public TokenServiceImpl(UserManager userManager, IpTokenBindRepo ipTokenBindRepo, IpUserBindRepo ipUserBindRepo){
        this.userManager = userManager;
        this.ipTokenBindRepo = ipTokenBindRepo;
        this.ipUserBindRepo = ipUserBindRepo;
    }

    public Optional<String> verifyAndGetResponseJson(UserDTO userDto, String password, String ip){
        return verifyAndGetUserStatus(userDto, password, ip).map(JsonUtils::toJson);
    }

    public Optional<String> verifyAndGetEncryptedResponseJson(UserDTO userDTO, String password, String ip){
        return verifyAndGetUserStatus(userDTO,password, ip).map(userStatus -> {
            SM4Utils sm4Utils = new SM4Utils();
            sm4Utils.setSecretKey(externalConfig.getSecretKey());
            sm4Utils.setIv(externalConfig.getIv());
            String encryptedUsername = sm4Utils.encryptData_CBC(userStatus.getUsername());
            String encryptedToken  = sm4Utils.encryptData_CBC(userStatus.getToken());
            String encryptedRefreshToken  = sm4Utils.encryptData_CBC(userStatus.getRefreshToken());
            List<String> encryptedRoles = userStatus.getRoles()
                    .stream()
                    .map(sm4Utils::encryptData_CBC)
                    .collect(Collectors.toList());

            userStatus.setUsername(encryptedUsername);
            userStatus.setToken(encryptedToken);
            userStatus.setRefreshToken(encryptedRefreshToken);
            userStatus.setRoles(encryptedRoles);

            return userStatus;
        }).map(JsonUtils::toJson);
    }

    public Optional<UserStatus> verifyAndGetUserStatus(UserDTO userDto, String password, String ip){
        boolean loginStatus = false;
        StringBuilder message = new StringBuilder().append("???????????????");

        if (userDto.getLocked() || userDto.getTimesOfLoginFailure() > (Integer) SystemConfig.get(AuthConstants.MAX_LOGIN_FAILURE_TIMES)) {
                message.append("??????????????????????????????????????????!");
        } else if(Objects.equals(userDto.getUserType(), UserTypeEnum.NORMAL)
                && (userDto.getExpired() || TimeUtils.isSameOrBefore(userDto.getPasswordExpiredTimestamp()))) {
            message.append("???????????????????????????????????????????????????!");
            userDto.setExpired(true);
        } else if(!CommonUtils.checkPassword(password, userDto.getPassword())){
            message.append("??????????????????????????????????????????!");
            if (!AuthConstants.ADMIN.equals(userDto.getUsername())) {
                userDto.setTimesOfLoginFailure(userDto.getTimesOfLoginFailure() + 1);

                if (userDto.getTimesOfLoginFailure() > (Integer) SystemConfig.get(AuthConstants.MAX_LOGIN_FAILURE_TIMES))
                    userDto.setLocked(true);
            }
        } else {
            loginStatus = true;
        }

        if (loginStatus) {
            message.delete(0, message.length()).append("????????????");
            userDto.setTimesOfLoginFailure(0);
        }

        UserDTO updatedUser = userManager.updateUserByUserDto(userDto);
        if (!loginStatus){
            updatedUser.setRoleName(Lists.newArrayList());
        }

        Optional<UserStatus> userStatus =  Optional.of(new UserStatus(updatedUser, message.toString(), "0"));
        if (loginStatus){
            Optional<String> refreshToken = CommonUtils.generateToken(updatedUser, refreshSecret, issuer, true);
            userStatus =  CommonUtils.generateToken(updatedUser, secret, issuer, false)
                    .map(token -> new UserStatus(updatedUser, token, message.toString(), "0", userDto.getId(), refreshToken.get()));
        }

        if (StringUtils.isNotEmpty(userStatus.get().getToken()) && userDto.getUserType() != UserTypeEnum.SCRIPT){
            if (!verifyIpAndUsernameBound(ip, userStatus.get().getUsername())){
                throw new CustomException(String.format("??????IP[%s]?????????[%s]?????????", ip, userStatus.get().getUsername()));
            }
        }
        return userStatus;
    }

    public Optional<String> decodeToken(String token, String ip){
        boolean loginStatus = false;
        StringBuilder message = new StringBuilder().append("???????????????");

        Claims claims = TokenUtils.getClaimsFromToken(refreshSecret, token);
        String username = (String) claims.get(TokenConstants.USERNAME);

        UserDTO userDto = userManager.getUserByUserName(username);

        if (userDto.getLocked() || userDto.getTimesOfLoginFailure() > (Integer) SystemConfig.get(AuthConstants.MAX_LOGIN_FAILURE_TIMES)) {
            message.append("??????????????????????????????????????????!");
        } else if(Objects.equals(userDto.getUserType(), UserTypeEnum.NORMAL)
                && (userDto.getExpired() || TimeUtils.isSameOrBefore(userDto.getPasswordExpiredTimestamp()))) {
            message.append("???????????????????????????????????????????????????!");
            userDto.setExpired(true);
        } else {
            loginStatus = true;
        }

        if (loginStatus) {
            message.delete(0, message.length()).append("????????????");
            userDto.setTimesOfLoginFailure(0);
        }
        UserDTO updatedUser = userManager.updateUserByUserDto(userDto);
        if (!loginStatus){
            updatedUser.setRoleName(null);
        }
        Optional<UserStatus> userStatus =  Optional.of(new UserStatus(updatedUser, message.toString(), "0"));
        if (loginStatus){
            userStatus = CommonUtils.generateToken(updatedUser, secret, issuer, false)
                    .map(t -> new UserStatus(updatedUser, t, message.toString(), "0", userDto.getId(), null));
        }

        if (StringUtils.isNotEmpty(userStatus.get().getToken()) && userDto.getUserType() != UserTypeEnum.SCRIPT){
            if (!verifyIpAndUsernameBound(ip, updatedUser.getUsername())){
                throw new CustomException(String.format("??????IP[%s]?????????[%s]?????????", ip, updatedUser.getUsername()));
            }
        }
        return userStatus.map(JsonUtils::toJson);
    }

    @Override
    @Transactional
    public void upsertIpTokenBind(String ip, String token){
        if (StringUtils.isEmpty(token) || StringUtils.isEmpty(ip)){
            throw new CustomException("IP?????????token???????????????");
        }
        IpTokenBindDbo ipTokenBindDbo;
        Optional<IpTokenBindDbo> tokenBind = ipTokenBindRepo.findByIpaddr(ip);
        if (tokenBind.isPresent()){
            ipTokenBindDbo = tokenBind.get();
            ipTokenBindDbo.setToken(token);
        }else {
            ipTokenBindDbo = new IpTokenBindDbo(ip, token);
        }
        ipTokenBindRepo.save(ipTokenBindDbo);
    }

    @Override
    public Boolean verifyIpAndTokenBound(String ip, String token) {
        if (StringUtils.isEmpty(token) || StringUtils.isEmpty(ip)){
            return false;
        }
        Optional<IpTokenBindDbo> tokenBind = ipTokenBindRepo.findByIpaddr(ip);
        if (tokenBind.isPresent() && token.equals(tokenBind.get().getToken())){
            return true;
        }
        return false;
    }

    @Override
    public Boolean verifyIpAndUsernameBound(String ip, String username) {
        if (StringUtils.isEmpty(ip) || StringUtils.isEmpty(username)){
            return false;
        }
        if (!ipUserBindRepo.findAllByUsername(username).isPresent()){
            return true;
        }
        if (ipUserBindRepo.findAllByIpAndUsername(ip, username).isPresent()){
            return true;
        };
        return false;
    }
}
