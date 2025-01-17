package com.msh.room;

import com.msh.room.cache.RoomStateDataRepository;
import com.msh.room.dto.event.JudgeEvent;
import com.msh.room.dto.event.JudgeEventType;
import com.msh.room.dto.event.PlayerEvent;
import com.msh.room.dto.event.PlayerEventType;
import com.msh.room.dto.response.JudgeDisplayInfo;
import com.msh.room.dto.response.PlayerDisplayInfo;
import com.msh.room.dto.room.RoomStateData;
import com.msh.room.dto.room.RoomStatus;
import com.msh.room.dto.room.seat.PlayerSeatInfo;
import com.msh.room.model.role.Roles;
import com.msh.room.model.room.Room;
import com.msh.room.model.room.RoomManager;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Created by zhangruiqian on 2017/5/23.
 */
public class HunterTest {
    private RoomManager roomManager;
    private RoomStateDataRepository repository;
    private String roomCode = "abc";

    @Before
    public void setup() {
        repository = new RoomStateDataRepository();
        roomManager = new RoomManager();
        roomManager.setDataRepository(repository);
        //房间空闲
        RoomStateData data = new RoomStateData();
        data.setRoomCode(roomCode);
        data.setStatus(RoomStatus.VACANCY);
        repository.putRoomStateData(roomCode, data);
        Room room = roomManager.loadRoom(roomCode);

        //create
        JudgeEvent createRoomEvent = new JudgeEvent(roomCode, JudgeEventType.CREATE_ROOM);
        Map<Roles, Integer> gameConfig = new HashMap<>();
        gameConfig.put(Roles.VILLAGER, 4);
        gameConfig.put(Roles.WEREWOLVES, 4);
        gameConfig.put(Roles.WITCH, 1);
        gameConfig.put(Roles.HUNTER, 1);
        gameConfig.put(Roles.SEER, 1);
        gameConfig.put(Roles.MORON, 1);
        createRoomEvent.setGameConfig(gameConfig);
        room.resolveJudgeEvent(createRoomEvent);
        //joinAll
        for (int i = 1; i < 13; i++) {
            int seatNumber = i;
            String userId = "Richard_" + i;
            PlayerEvent event = new PlayerEvent(PlayerEventType.JOIN_ROOM, seatNumber, userId);
            room.resolvePlayerEvent(event);
        }
        JudgeEvent completeEvent = new JudgeEvent(roomCode, JudgeEventType.COMPLETE_CREATE);
        room.resolveJudgeEvent(completeEvent);
    }

    @Test
    public void wolfKillHunter() {
        Room room = roomManager.loadRoom(roomCode);
        JudgeEvent nightComingEvent = new JudgeEvent(roomCode, JudgeEventType.NIGHT_COMING);
        room.resolveJudgeEvent(nightComingEvent);

        JudgeEvent wolfKillEvent = new JudgeEvent(roomCode, JudgeEventType.WOLF_KILL);
        RoomStateData stateData = repository.loadRoomStateData(roomCode);
        //杀个猎人
        int hunter = stateData.getAliveSeatByRole(Roles.HUNTER);
        wolfKillEvent.setWolfKillNumber(hunter);
        room.resolveJudgeEvent(wolfKillEvent);
        JudgeEvent seerVerifyEvent = new JudgeEvent(roomCode, JudgeEventType.SEER_VERIFY);
        //验个狼
        int wolfSeat = stateData.getAliveSeatByRole(Roles.WEREWOLVES);
        seerVerifyEvent.setSeerVerifyNumber(wolfSeat);
        room.resolveJudgeEvent(seerVerifyEvent);
        //没救
        JudgeEvent witchSaveEvent = new JudgeEvent(roomCode, JudgeEventType.WITCH_SAVE);
        witchSaveEvent.setWitchSave(false);
        room.resolveJudgeEvent(witchSaveEvent);
        JudgeEvent witchPoisonEvent = new JudgeEvent(roomCode, JudgeEventType.WITCH_POISON);
        //也没毒
        witchPoisonEvent.setWitchPoisonNumber(0);
        room.resolveJudgeEvent(witchPoisonEvent);

        JudgeEvent daytimeEvent = new JudgeEvent(roomCode, JudgeEventType.DAYTIME_COMING);
        JudgeDisplayInfo judgeDisplayInfo = room.resolveJudgeEvent(daytimeEvent);
        //猎人时间
        assertEquals(RoomStatus.HUNTER_SHOOT, judgeDisplayInfo.getStatus());
        //法官暂时没有办法操作
        assertEquals(Arrays.asList(JudgeEventType.RESTART_GAME, JudgeEventType.DISBAND_GAME), judgeDisplayInfo.getAcceptableEventTypes());
        String hunterUserName = "";
        for (int i = 1; i < 13; i++) {
            PlayerDisplayInfo displayResult = room.getPlayerDisplayResult(i);
            PlayerSeatInfo playerInfo = displayResult.getPlayerInfo();
            if (Roles.HUNTER.equals(playerInfo.getRole())) {
                hunterUserName = playerInfo.getUserID();
                //猎人可以操作带人
                assertEquals(Arrays.asList(PlayerEventType.HUNTER_SHOOT), displayResult.getAcceptableEventTypeList());
                //猎人已死
                assertFalse(playerInfo.isAlive());
                //夜晚死人信息是猎人
                assertEquals(Arrays.asList(i), displayResult.getNightRecord().getDiedNumber());
            }
        }

        PlayerEvent hunterShoot = new PlayerEvent(PlayerEventType.HUNTER_SHOOT, hunter, hunterUserName);
        //把狼崩了
        hunterShoot.setShootNumber(wolfSeat);
        PlayerDisplayInfo playerDisplayInfo = room.resolvePlayerEvent(hunterShoot);
        //猎人操作完成，没其他操作了
        assertEquals(0, playerDisplayInfo.getAcceptableEventTypeList().size());

        JudgeDisplayInfo judgeDisplayResult = room.getJudgeDisplayResult();
        //进入白天
        assertEquals(RoomStatus.DAYTIME, judgeDisplayResult.getStatus());
        //法官可以发起投票
        assertEquals(Arrays.asList(JudgeEventType.DAYTIME_VOTING, JudgeEventType.RESTART_GAME, JudgeEventType.DISBAND_GAME),
                judgeDisplayResult.getAcceptableEventTypes());

        //法官看到狼死了
        assertFalse(judgeDisplayResult.getPlayerSeatInfoList().get(wolfSeat - 1).isAlive());

        for (int i = 1; i < 13; i++) {
            PlayerDisplayInfo displayResult = room.getPlayerDisplayResult(i);
            PlayerSeatInfo playerInfo = displayResult.getPlayerInfo();
            if (i == wolfSeat) {
                //玩家看到狼死了
                assertFalse(playerInfo.isAlive());
            }
        }

    }


    @Test
    public void witchPoisonHunter() {
        Room room = roomManager.loadRoom(roomCode);
        JudgeEvent nightComingEvent = new JudgeEvent(roomCode, JudgeEventType.NIGHT_COMING);
        room.resolveJudgeEvent(nightComingEvent);

        JudgeEvent wolfKillEvent = new JudgeEvent(roomCode, JudgeEventType.WOLF_KILL);
        RoomStateData stateData = repository.loadRoomStateData(roomCode);
        //杀个猎人
        int seat = stateData.getAliveSeatByRole(Roles.VILLAGER);
        wolfKillEvent.setWolfKillNumber(seat);
        room.resolveJudgeEvent(wolfKillEvent);
        JudgeEvent seerVerifyEvent = new JudgeEvent(roomCode, JudgeEventType.SEER_VERIFY);
        //验个狼
        int wolfSeat = stateData.getAliveSeatByRole(Roles.WEREWOLVES);
        seerVerifyEvent.setSeerVerifyNumber(wolfSeat);
        room.resolveJudgeEvent(seerVerifyEvent);
        //没救
        JudgeEvent witchSaveEvent = new JudgeEvent(roomCode, JudgeEventType.WITCH_SAVE);
        witchSaveEvent.setWitchSave(false);
        room.resolveJudgeEvent(witchSaveEvent);
        JudgeEvent witchPoisonEvent = new JudgeEvent(roomCode, JudgeEventType.WITCH_POISON);
        //毒猎人
        int hunterSeat = stateData.getAliveSeatByRole(Roles.HUNTER);
        witchPoisonEvent.setWitchPoisonNumber(hunterSeat);
        room.resolveJudgeEvent(witchPoisonEvent);

        JudgeEvent daytimeEvent = new JudgeEvent(roomCode, JudgeEventType.DAYTIME_COMING);
        JudgeDisplayInfo judgeDisplayInfo = room.resolveJudgeEvent(daytimeEvent);
        //正常天亮
        assertEquals(RoomStatus.DAYTIME, judgeDisplayInfo.getStatus());
        //正常发言
        assertEquals(Arrays.asList(JudgeEventType.DAYTIME_VOTING, JudgeEventType.RESTART_GAME, JudgeEventType.DISBAND_GAME), judgeDisplayInfo.getAcceptableEventTypes());
        for (int i = 1; i < 13; i++) {
            PlayerDisplayInfo displayResult = room.getPlayerDisplayResult(i);
            PlayerSeatInfo playerInfo = displayResult.getPlayerInfo();
            if (Roles.HUNTER.equals(playerInfo.getRole())) {
                //猎人死了
                assertFalse(playerInfo.isAlive());
            }
        }
    }

    @Test
    public void testVoteHunter() {
        Room room = roomManager.loadRoom(roomCode);
        JudgeEvent nightComingEvent = new JudgeEvent(roomCode, JudgeEventType.NIGHT_COMING);
        room.resolveJudgeEvent(nightComingEvent);

        JudgeEvent wolfKillEvent = new JudgeEvent(roomCode, JudgeEventType.WOLF_KILL);
        RoomStateData stateData = repository.loadRoomStateData(roomCode);
        //杀个猎人
        int villager = stateData.getAliveSeatByRole(Roles.VILLAGER);
        wolfKillEvent.setWolfKillNumber(villager);
        room.resolveJudgeEvent(wolfKillEvent);
        JudgeEvent seerVerifyEvent = new JudgeEvent(roomCode, JudgeEventType.SEER_VERIFY);
        //验个狼
        int wolfSeat = stateData.getAliveSeatByRole(Roles.WEREWOLVES);
        seerVerifyEvent.setSeerVerifyNumber(wolfSeat);
        room.resolveJudgeEvent(seerVerifyEvent);
        //没救
        JudgeEvent witchSaveEvent = new JudgeEvent(roomCode, JudgeEventType.WITCH_SAVE);
        witchSaveEvent.setWitchSave(false);
        room.resolveJudgeEvent(witchSaveEvent);
        JudgeEvent witchPoisonEvent = new JudgeEvent(roomCode, JudgeEventType.WITCH_POISON);
        //也没毒
        witchPoisonEvent.setWitchPoisonNumber(0);
        room.resolveJudgeEvent(witchPoisonEvent);

        JudgeEvent daytimeEvent = new JudgeEvent(roomCode, JudgeEventType.DAYTIME_COMING);
        room.resolveJudgeEvent(daytimeEvent);
        JudgeEvent daytimeVoteEvent = new JudgeEvent(roomCode, JudgeEventType.DAYTIME_VOTING);
        room.resolveJudgeEvent(daytimeVoteEvent);

        int hunter = stateData.getAliveSeatByRole(Roles.HUNTER);
        for (int i = 1; i < 13; i++) {
            PlayerDisplayInfo displayResult = room.getPlayerDisplayResult(i);
            PlayerSeatInfo playerInfo = displayResult.getPlayerInfo();
            if (!playerInfo.isAlive()) {
                continue;
            }
            PlayerEvent voteEvent = new PlayerEvent(PlayerEventType.DAYTIME_VOTE, i, playerInfo.getUserID());
            voteEvent.setDaytimeVoteNumber(hunter);
            room.resolvePlayerEvent(voteEvent);
        }

        JudgeDisplayInfo judgeDisplayResult = room.getJudgeDisplayResult();

        //猎人时间
        assertEquals(RoomStatus.HUNTER_SHOOT, judgeDisplayResult.getStatus());
        assertEquals(Arrays.asList(JudgeEventType.RESTART_GAME, JudgeEventType.DISBAND_GAME), judgeDisplayResult.getAcceptableEventTypes());
        String hunterUserName = "";
        for (int i = 1; i < 13; i++) {
            PlayerDisplayInfo displayResult = room.getPlayerDisplayResult(i);
            PlayerSeatInfo playerInfo = displayResult.getPlayerInfo();
            if (playerInfo.getRole().equals(Roles.HUNTER)) {
                hunterUserName = playerInfo.getUserID();
                assertEquals(Arrays.asList(PlayerEventType.HUNTER_SHOOT), displayResult.getAcceptableEventTypeList());
                assertFalse(playerInfo.isAlive());
            }
        }

        PlayerEvent hunterShoot = new PlayerEvent(PlayerEventType.HUNTER_SHOOT, hunter, hunterUserName);
        //把狼崩了
        hunterShoot.setShootNumber(wolfSeat);
        PlayerDisplayInfo playerDisplayInfo = room.resolvePlayerEvent(hunterShoot);
        assertEquals(0, playerDisplayInfo.getAcceptableEventTypeList().size());
//
        JudgeDisplayInfo judgeDisplayInfo = room.getJudgeDisplayResult();
        //投票阶段,投票已完成
        assertEquals(RoomStatus.VOTING, judgeDisplayInfo.getStatus());
        //可以进入黑夜
        assertEquals(Arrays.asList(JudgeEventType.NIGHT_COMING, JudgeEventType.RESTART_GAME, JudgeEventType.DISBAND_GAME),
                judgeDisplayInfo.getAcceptableEventTypes());
//
        for (int i = 1; i < 13; i++) {
            PlayerDisplayInfo displayResult = room.getPlayerDisplayResult(i);
            if (i == wolfSeat) {
                //狼死了
                assertFalse(displayResult.getPlayerInfo().isAlive());
            }
        }

    }
}
