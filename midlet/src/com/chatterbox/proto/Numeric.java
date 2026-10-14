package com.chatterbox.proto;

/**
 * Numeric replies the client cares about, as three-digit strings.
 *
 * Kept as constants rather than an int array because CLDC has no enum and
 * because "433" is far more readable than 433.
 */
public final class Numeric {

    public static final String RPL_WELCOME = "001";
    public static final String RPL_YOURHOST = "002";
    public static final String RPL_CREATED = "003";
    public static final String RPL_MYINFO = "004";
    public static final String RPL_ISUPPORT = "005";

    public static final String RPL_AWAY = "301";
    public static final String RPL_WHOISUSER = "311";
    public static final String RPL_ENDOFWHO = "315";
    public static final String RPL_ENDOFWHOIS = "318";
    public static final String RPL_CHANNELMODEIS = "324";
    public static final String RPL_NOTOPIC = "331";
    public static final String RPL_TOPIC = "332";
    public static final String RPL_TOPICWHOTIME = "333";
    public static final String RPL_INVITING = "341";
    public static final String RPL_SUMMONING = "346";
    public static final String RPL_WHOISCHANNELS = "319";

    public static final String RPL_NAMREPLY = "353";
    public static final String RPL_ENDOFNAMES = "366";

    public static final String ERR_NOSUCHNICK = "401";
    public static final String ERR_NOSUCHSERVER = "402";
    public static final String ERR_NOSUCHCHANNEL = "403";
    public static final String ERR_CANNOTSENDTOCHAN = "404";
    public static final String ERR_NOMOTD = "422";
    public static final String ERR_ERRONEUSNICKNAME = "432";
    public static final String ERR_NICKNAMEINUSE = "433";
    public static final String ERR_NICKCOLLISION = "436";
    public static final String ERR_USERNOTINCHANNEL = "441";
    public static final String ERR_NOTONCHANNEL = "442";
    public static final String ERR_USERONCHANNEL = "443";
    public static final String ERR_NOTREGISTERED = "451";
    public static final String ERR_NEEDMOREPARAMS = "461";
    public static final String ERR_ALREADYREGISTED = "462";
    public static final String ERR_PASSWDMISMATCH = "464";
    public static final String ERR_YOUREBANNEDCREEP = "465";
    public static final String ERR_CHANNELISFULL = "471";
    public static final String ERR_UNKNOWNMODE = "472";
    public static final String ERR_INVITEONLYCHAN = "473";
    public static final String ERR_BANNEDFROMCHAN = "474";
    public static final String ERR_BADCHANNELKEY = "475";
    public static final String ERR_BADCHANMASK = "476";
    public static final String ERR_NOCHANMODES = "477";
    public static final String ERR_LISTFULL = "478";
    public static final String ERR_NOPRIVILEGES = "481";

    private Numeric() {
    }

    public static boolean isError(String cmd) {
        if (cmd == null || cmd.length() != 3) {
            return false;
        }
        return cmd.charAt(0) == '4' || cmd.charAt(0) == '5';
    }

    public static boolean isReply(String cmd) {
        if (cmd == null || cmd.length() != 3) {
            return false;
        }
        // 001-005 are the RPL_WELCOME family, so '0' counts as a reply too.
        char c = cmd.charAt(0);
        return c == '0' || c == '1' || c == '2' || c == '3';
    }

    public static boolean isHandled(String cmd) {
        return RPL_WELCOME.equals(cmd)
                || RPL_NOTOPIC.equals(cmd)
                || RPL_TOPIC.equals(cmd)
                || RPL_NAMREPLY.equals(cmd)
                || RPL_ENDOFNAMES.equals(cmd)
                || RPL_WHOISUSER.equals(cmd)
                || RPL_WHOISCHANNELS.equals(cmd)
                || RPL_AWAY.equals(cmd)
                || RPL_INVITING.equals(cmd)
                || RPL_CHANNELMODEIS.equals(cmd)
                || RPL_ISUPPORT.equals(cmd)
                || ERR_NOSUCHNICK.equals(cmd)
                || ERR_NOSUCHSERVER.equals(cmd)
                || ERR_NOSUCHCHANNEL.equals(cmd)
                || ERR_CANNOTSENDTOCHAN.equals(cmd)
                || ERR_NICKNAMEINUSE.equals(cmd)
                || ERR_UNKNOWNCOMMAND.equals(cmd);
    }

    /** Numeric for "unknown command", which is not in the 4xx range above. */
    public static final String ERR_UNKNOWNCOMMAND = "421";
}
