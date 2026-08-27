# 闂佸搫鎳樼紓姘跺礂濡粯濯撮柟鐐綑铻氶梺?API 濠电偛顦崝宀勫船閼恒儳鈹嶉柍鈺佸暕缁辨牠鏌熼梹鎰樂缂佺姵鐟╁畷?Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 闁荤姳璁查弲婊冣枍閵堝鍤勯柟瀛樺笧缁夎偐绱掗悪鍛？闁诡喖锕幆鍐礋椤掆偓铻氶梺?API 闁荤姳绀佹晶浠嬫偪閸℃稑鎹堕柕濞垮€楃粻浠嬫倵濞戞鎴炴櫠閻ｅ本鍋橀悘鐐跺Г瑜般儵鏌ｉ幘妤€鎳忛悾閬嶆煕濮橆剚婀版俊鐐插€块弫宥囦沪閹呮Х婵炴垶鏌ㄩ幊搴ｂ偓鍨叀瀹曟鈧湱濮崇划鎾绘煟?`api_registry` 闁荤姳鐒﹀妯肩礊?ID闂佹寧绋戦懟顖炴嚐閻旂厧鎹堕柕濞у懏銇濋柡澶婄墛閸ㄨ泛顪冮崒鐐茬倞闁绘劕鐡ㄩ埢鏃堟煏?
**Architecture:** 闂佸憡鑹惧ù鐑筋敂椤掑嫬鎹?`ai_agent_skill_config` 闁荤偞绋忛崝瀣嚈閹达箑妫橀柡澶嬵儥閺夊鏌涘▎妯虹仯闁宠鐗滈埀顒佺⊕椤ㄥ棝顢?`api_registry_id`闂佹寧绋戦悺鏄慜闂侀潧妫旂欢姘躲€傞锕€鏄ラ柣鏂款殠閸ょ娀鎮归悘鐑樺閸嬫挻绋夐·澶緾 婵炲濮甸幐鎼佸磻瀹ュ妞界€光偓閳ь剟顢橀幖浣瑰仩闁糕剝蓱缁犳盯鏌涢弬琛″亾閸愭彃浼庨梻浣哄亾瀹曟﹢鎯屾ィ鍐╃劵闊洤娴烽悙濠囨偣閸ャ儱鍔氶柣鈯欏啠鏋旈柣鎰帨閸嬫捇宕掑顒夋瀫缂備焦妫忛崹鏉匡耿?API 濠电偛顦崝宀勫船閻ｅ本鍋橀柕濞炬杹閸嬫挻寰勭€ｎ亶浠撮梺纭呯焽閸屾侗鍋ㄩ梺鍛婄矊閺堫剚淇婅閹虫宕愰悢绮瑰亾閸愵喖纭€闁哄洦淇洪崢顒傜磽娴ｅ摜鎽犲┑?`apiRegistryId`闂佹寧绋戦惌鍌炲焵椤掆偓椤︽壆鈧哎鍔戦獮鎺楀Ψ閵夈儳绋夐梺鍝勫暢濞夋盯鏁愰悙鍝勭闁告侗鍠楅梽宥嗙節?ID 婵炴垶鎸哥€涒晜鏅堕悾灞惧仒閻忕偠濮よぐ銉╂煟閹炬绉剁粈澶嬬箾閹捐櫕鍣介柍瑙勭墵閺屽懏寰勭€ｎ亶浠撮梺鍝勫暢濞夋稖銇愯閵嗘帡宕ㄩ妤佹櫈闂佸搫顦崕鑼姳?ID闂?
**Tech Stack:** Java 17闂侀潧妫旂拋顤秗ing Boot 3.4.5闂侀潧妫旂拋顤秗ing JDBC闂侀潧妫旂粋寤it 5闂侀潧妫旂粩绔ertJ闂侀潧妫旂拋顤秗ing MockMvc闂侀潧妫旂粻鎼杄 3闂侀潧妫旂粭鎶ment Plus闂侀潧妫旈懙鎭沝e.js `node --test`闂侀潧妫旂粻鎼僼e闂?
## Global Constraints

- 闂佸湱顣介崑鎾绘煛閸繍妲归柡灞斤攻閺呭爼鎮欓弶鎴犱粣婵烇絽娴傞崰妤呭极婵傚憡鍎?Markdown 闂佸搫鍊稿ú锕傚Υ閸屾繄鐤€闁告稒鐣埀顒€绻戦幏鍛崉閵婏附娈㈡繛鎴炴惄閸樹粙寮搁崘顔嘉?- 缂備礁鍊烽悞锕傤敆濞戞瑦濯撮悹鎭掑妽閺?SQLite闂侀潧妫旂粭? 缂備焦绋戦ˇ鏉匡耿娴兼潙鎹堕柣妤€鐗婂▓鍫曟煙鐠団€虫灈缂併劑浜堕弫宥呪槈濞嗘劗鍘愭繝鈷€鍛沪闁哄棛鍠栭獮鎴︻敊閸撗勫皾闂佸搫顑呯€氼剝銇愯濡啴濮€閻樺啿鈧?MySQL闂?- 闂佸憡鑹惧ù鐑筋敂?Java 濠电偛顦崝鎴﹀闯閹绢喗鐒奸梻鍫熺〒閺?`docs/rules/BACKEND_JAVA_COMMENT_RULES.md`闂?- 婵炲濯寸徊鍧楁偉濠婂懏鍋橀悘鐐靛亾濞堝爼姊洪鍕锭闁?`docs/rules/CODE_SIZE_RULES.md`闂?- Service 闁诲繒鍋涢崐鍫曞焵椤戣法顏筼ntroller 闁?Bean 婵炶揪缍€濞夋洟寮妶澶婄柧妞ゆ洍鍋撻柍?public 闂佸搫顑呯€氫即鍩€椤掑倸孝婵炲懏甯掗埢鏃堝Ω閵夈儱姹查梺鎸庣☉婵傛梻绮径瀣閻犳亽鍔嶉弳蹇涙倵濞戞瑯娈曟い鏂胯嫰閳绘棃濡搁妷銉ユ辈闂?- 婵炴垶鎸哥粔鍓佹椤忓懏缍?`target/`闂侀潧妫斿姊攊st/` 缂備焦绋戦ˇ閬嶅极閹捐绠ｉ柟閭︿邯閻涙捇鏌ｅ缁樻澓闁?- 闂佸憡鎸哥粔鍫曨敂椤掑倻鍗氭い鏍ㄨ壘缂嶆捇鏌涢妷銉︽儓闁伙絿鍋撶粙?15200闂佹寧绋戦張顒€锕㈤悧鍫㈩浄閻犲洦褰冮～銈呪槈閹惧磭小闁逛究鍔戝銊╂偡閺夋鏋€缂備焦妫忛崹鎶筋敂椤掑嫬鐭楅柨婵嗩槸鐢磭绱撻崘鎯ф灁闁?- 閻熸粎澧楅幐鍛婃櫠閻樺樊鍟呴柕澶堝€楃粙濠囨煕閺嵮勬儓闁告埊绻濆鍨緞鐎ｎ偄绱﹂梺鍦帛閸旀帒顫濋敃鍌氱哗闁荤喐婢樿闂佹寧绋掔粙鎺撴櫠閻ｅ本鍋橀悘鐐插悑瀹曟煡鏌涢弬琛″亾閸愬樊鏋€闂佺绻愰悧濠囨偂閿熺姵鍎戦悗锝庡墯缁愭鏌″鍛闁哄鍟粋?diff闂佹寧绋戞總鏃傛崲濮樿埖鍋╂繛鍡樺灦閺嗗繘鏌熼弶鎴濇Щ闁告埊绻濆鍨緞婵犲倻绉梺鍝勬处閻ｎ亪鍩€?
---

## File Structure

- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiSkillApiConfigRequest.java`
  - 闁荤姴娲弨閬嶆儑?DTO 闂佸搫鍊瑰姗€路?`apiRegistryId` 闁诲孩绋掗〃鍡涱敊瀹€鍕煑?getter/setter闂?- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiSkillApiConfigResponse.java`
  - 闂佸憡绻傜粔瀵歌姳?record 闂佸搫鍊瑰姗€路?`apiRegistryId` 闁诲孩绋掗〃鍡涱敊瀹€鍕?- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiManagedSkillConfig.java`
  - 闂佽桨鑳舵晶妤€鐣垫担瑙勫劅闁规儳纾弶钘壝归敐鍡樺鞍闁哄苯锕ラ弲?`apiRegistryId` 闁诲繒鍋熼崑鐐哄焵椤戭兛璁查崑?- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiManagedSkillConfigSchemaInitializer.java`
  - 閻庣偣鍊濈紓姘跺Υ?SQL 婵犫拃鍛粶濠?`api_registry_id`闂?  - `initialize()` 閻庣偣鍊濈紓姘跺Υ閸愵喖瑙﹂幖娣灩閳锋棃鎮跺☉妯垮闁烩姍鍐ｆ灁闂傚牊绋撻幗鐘绘煕閿斿搫濡介柍褜鍎搁崟鍨挄闂佸搫琚崕鑽ゆ濠靛牏纾介柛婵嗗閻忔瑩鏌￠崘鈺佸姸闁归鍏樺畷婵嬫偐閼碱剚鎲诲┑鐐茬墢閸嬶綁鍩€?- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/JdbcAiManagedSkillConfigRepository.java`
  - SQL 闂佸搫琚崕鎾敋濡ゅ懎违濞达絿鎳撶徊濠氭煕韫囧鍔橀柍褜鍏涢悞锕€煤閸ф妫橀柤鍓插厴閸嬫挻鎷呯粙鎸庮棟闂佽桨鐒︽竟鍡欏垝閿旈敮鍋撶憴鍕闁归攱澹嗛幃鎵沪閻愵儷渚€鎮樿箛鎾愁仾闁轰緡鍣ｉ獮?`api_registry_id`闂?- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiAgentManagementService.java`
  - 闂佸憡甯楃粙鎴犵磽閹捐违濞达綀娅ｅ浠嬪级閸喎鐏熼柍褜鍏涚粈浣哥暦閻斿吋鍋戞い鎴ｆ硶缁犱粙鎮?API 闂備焦婢樼粔鍫曟偪閸℃稑违濞达絿顭堥悘娆撴偠濞戞牕濡兼繛鍙夌墵瀵即鎮欓渚囨Н闂備緡鍋勭换瀣?`apiRegistryId`闂?- Modify: `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/management/AiManagedSkillConfigSchemaInitializerTest.java`
  - 闁荤喐娲栧Λ娑樏烘繝鍥ф闁哄娉曠槐锕傛偠濞戞牕濡奸悗鍨耿瀹曘儵顢曢姀銏℃喕濠电偛鐗忛崑锝夊焵椤戣法顦﹂柛鎴磿閳ь剚绋掗敋婵犫偓椤忓棙鍋橀柕濞у啰绠掗柣搴㈢⊕椤ㄥ棝顢欏畝鍕睄闁诡垱婢樺▓浼存煕閺傝濮€閽樼喖鏌涢幒鎾变粻闁?- Modify: `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/management/JdbcAiManagedSkillConfigRepositoryTest.java`
  - 闁荤喐娲栧Λ娑樏烘繝鍥ф闁哄顑欓弶濠氭煏閸℃洜鍔嶉柣鎿勭磿閹风娀锝為钘変壕濞达綀顫夌痪顖炴煛閸屾繂鐒介柍褜鍏涢悞锔剧博閼姐倗鐭?`apiRegistryId`闂?- Modify: `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/management/AiAgentManagementServiceTest.java`
  - 闁荤喐娲栧Λ娑樏烘繝鍥у珘鐎广儱鎳庨～銈夋倶閻愭彃鈧悂宕归崡鐑嗗殘闁诡厽宸婚崑鎾存媴閾忚銇濋柡澶婄墛閸垶鍩€椤戣法顦︾€规洜鍠栭幃顏堫敄鐠恒劎顔旈柣?API 闂備焦婢樼粔鍫曟偪閸℃稒鍎嶉柛鏇ㄥ墯闂勫秵绻?ID 婵烇絽娲︾换鍌炴偤閵婏妇鈻旈幖绮瑰墲缁€鈧梺鍝勫閸曟﹢鍩€?- Modify: `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/management/AiAgentManagementControllerTest.java`
  - 闁荤喐娲栧Λ娑樏?JSON 闁荤姴娲弨閬嶆儑娴兼潙妞介悘鐐跺Г閹瑩骞栫€涙ɑ鐓ｉ柤鍨灴閹?`apiConfig.apiRegistryId`闂?- Modify: `frontend/modules/ai/src/utils/api-registry.js`
  - `mergeRegistryApiConfig()` 闁哄鏅滈弻銊ッ?`apiRegistryId`闂?- Modify: `frontend/modules/ai/src/utils/api-registry-search.js`
  - 濠电偞鎸搁幊鎰板煘閺嶎厽鐒诲璺侯儏椤忋儵鏌￠崘顓熺【婵炲弶鐗滈幏?`onClear()`闂佹寧绋戞總鏃傛崲濮樿埖鍋╂繛鍡樺灥缁犳盯鏌?`onSelect()` 闁荤偞绋戞總鏃傛嫻閻旂厧违?- Modify: `frontend/modules/ai/src/components/AiApiRegistrySelect.vue`
  - 闂佽　鍋撴い鏍ㄧ☉閻?`v-model`闂侀潧妫旈悞锔剧博閼姐倗鐭氶弶鐐村閻ㄦ垵霉閻樿櫣鍫柍褜鍏涢懗鍓佹椤忓懏缍囬柟瀵稿亹閸嬫挻鎷呯粙鍨闁圭厧鐡ㄥú鐔煎焵椤掆偓椤︾敻濡村澶婂強闁告挆浣风驳闂?- Modify: `frontend/modules/ai/src/views/AiAgentManage.vue`
  - 闂佺懓鐏堥崑鎾绘煠閸愭祴鍋撻悢绮瑰亾閸愵喖纭€闁哄洨濮靛Ο濠囨煙闊彃鈧洟鎳熼悢鐓庣闁归偊浜濋崬?`apiConfig.apiRegistryId`闂?- Modify: `frontend/modules/ai/tests/api-registry-selection.test.mjs`
  - 闁荤喐娲栧Λ娑樏烘繝鍥х鐎广儱娴傛导鍌炴⒑椤愩埄妲归悗姘ュ姂婵″瓨鎷呴崷顓т紩缂備礁鏈禍浠嬪焵椤戣法顦︽繛鍙夌墵瀵増绌遍幍浣镐壕濞达絽顫曢埀顒€鍟村畷锟犲即閻旈鍊掓繛瀛樺殠閸婃繂顭囬妶澶婄畱濞达絿鐡斿鎰版煕閹烘挾鎳€闁?
---

### Task 1: 闂佸憡鑹惧ù鐑筋敂?DTO 婵炴垶鎸搁柊锝夈€傞锕€鏄ラ柣鏂款殠閸ょ娀鎮归悙鈺佸⒉濠⒀嗘硶娴?
**Files:**
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiSkillApiConfigRequest.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiSkillApiConfigResponse.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiManagedSkillConfig.java`
- Test: `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/management/AiAgentManagementControllerTest.java`

**Interfaces:**
- Consumes: existing `AiSkillApiConfigRequest` JavaBean, `AiSkillApiConfigResponse` record, `AiManagedSkillConfig` JavaBean.
- Produces:
  - `Long AiSkillApiConfigRequest#getApiRegistryId()`
  - `void AiSkillApiConfigRequest#setApiRegistryId(Long apiRegistryId)`
  - `AiSkillApiConfigResponse(boolean enabled, String baseUrl, String path, String method, Map<String, String> headers, int timeoutMillis, Long apiRegistryId)`
  - `Long AiManagedSkillConfig#getApiRegistryId()`
  - `void AiManagedSkillConfig#setApiRegistryId(Long apiRegistryId)`

- [ ] **Step 1: Write the failing controller JSON test**

Add this assertion to `createsUpdatesAndDeletesManagedApiSkill()` in `AiAgentManagementControllerTest.java` after the create request:

```java
.andExpect(jsonPath("$.apiConfig.apiRegistryId").value(1001))
```

Add `"apiRegistryId": 1001,` inside the create payload `apiConfig`:

```json
"apiConfig": {
  "apiRegistryId": 1001,
  "enabled": true,
  "baseUrl": "https://api.example.com",
  "path": "/quality",
  "method": "POST",
  "headers": {
    "X-App-Id": "demo-app"
  },
  "timeoutMillis": 3000
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl modules/ai-agent-spring-boot-starter -Dtest=AiAgentManagementControllerTest#createsUpdatesAndDeletesManagedApiSkill test
```

Expected: FAIL because `apiConfig.apiRegistryId` is not present in response.

- [ ] **Step 3: Add DTO and entity fields**

In `AiSkillApiConfigRequest.java`, add field and accessors:

```java
private Long apiRegistryId;

public Long getApiRegistryId() {
    return apiRegistryId;
}

public void setApiRegistryId(Long apiRegistryId) {
    this.apiRegistryId = apiRegistryId;
}
```

In `AiSkillApiConfigResponse.java`, add the last record component:

```java
public record AiSkillApiConfigResponse(
        boolean enabled,
        String baseUrl,
        String path,
        String method,
        Map<String, String> headers,
        int timeoutMillis,
        Long apiRegistryId) {
}
```

In `AiManagedSkillConfig.java`, add field and accessors near `promptTemplateId`:

```java
/**
 * 闂佺绻愰悿鍥ㄧ閸儲鍎嶉柛鏇ㄥ弮閸忓洨绱?API 濠电偛顦崝宀勫船閻ｅ本鍋橀柕濠庣厛閸炲墎鎲?ID闂佹寧绋戞總鏃傛嫻閻旇櫣鐭氬Δ锕侊骏閳ь剙鍟扮划鍫ユ倻濡警鏉归悗瑙勬偠閸庣敻宕㈤妶鍥╃＞妞ゆ棁妫勯悘妤呮煕濡儤顥滅憸鏉跨埣瀵偊鎮ч崼婵堛偊闂? */
private Long apiRegistryId;

public Long getApiRegistryId() {
    return apiRegistryId;
}

public void setApiRegistryId(Long apiRegistryId) {
    this.apiRegistryId = apiRegistryId;
}
```

- [ ] **Step 4: Temporarily update response construction to compile**

In `AiAgentManagementService#apiConfig`, update the record constructor with a temporary `null`:

```java
return skillRegistry.apiSkillConfig(skillName)
        .map(config -> new AiSkillApiConfigResponse(
                config.enabled(),
                config.baseUrl(),
                config.path(),
                config.method(),
                redactedHeaders(config.headers()),
                config.timeoutMillis(),
                null))
        .orElse(null);
```

- [ ] **Step 5: Run test to verify it still fails for behavior**

Run:

```bash
mvn -pl modules/ai-agent-spring-boot-starter -Dtest=AiAgentManagementControllerTest#createsUpdatesAndDeletesManagedApiSkill test
```

Expected: FAIL because response value is `null` instead of `1001`.

- [ ] **Step 6: Commit task changes**

```bash
git add modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiSkillApiConfigRequest.java modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiSkillApiConfigResponse.java modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiManagedSkillConfig.java modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiAgentManagementService.java modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/management/AiAgentManagementControllerTest.java
git commit -m "feat(ai): expose skill api registry id contract"
```

---

### Task 2: 闂佽桨鑳舵晶妤€鐣垫担瑙勫劅闁规儳鍟块悘銉モ攽椤旂⒈鍎忛悗鍨叀瀹曟鎮℃惔顔兼櫍闂佸憡顨呭ú銊︻殽?
**Files:**
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiManagedSkillConfigSchemaInitializer.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/management/AiManagedSkillConfigSchemaInitializerTest.java`

**Interfaces:**
- Consumes: `AiManagedSkillConfigSchemaInitializer#initialize()`.
- Produces: startup DDL that creates or upgrades `ai_agent_skill_config.api_registry_id`.

- [ ] **Step 1: Write failing schema tests**

In `initializeCreatesMysqlTableWithOnlyPrimaryKeyIndex()`, add:

```java
assertThat(sql).contains("api_registry_id BIGINT NULL COMMENT '闂佺绻愰悿鍥ㄧ閸儲鍎嶉柛鎴炩攰I濠电偛顦崝宀勫船閻ｅ本鍋橀柍顐ｆ箚'");
```

Add a second test:

```java
@Test
void initializeAddsApiRegistryColumnWhenExistingTableMissesIt() {
    RecordingDataSource dataSource = new RecordingDataSource();
    dataSource.apiRegistryColumnExists = false;
    AiManagedSkillConfigSchemaInitializer initializer = new AiManagedSkillConfigSchemaInitializer(dataSource);

    initializer.initialize();

    assertThat(dataSource.sqlList).anySatisfy(sql ->
            assertThat(sql).contains("ALTER TABLE ai_agent_skill_config")
                    .contains("ADD COLUMN api_registry_id BIGINT NULL COMMENT '闂佺绻愰悿鍥ㄧ閸儲鍎嶉柛鎴炩攰I濠电偛顦崝宀勫船閻ｅ本鍋橀柍顐ｆ箚'"));
}
```

Extend `RecordingDataSource` with:

```java
private boolean apiRegistryColumnExists = true;
```

Expose executed SQL through:

```java
List<String> sqlList() {
    return sqlList;
}
```

Update `singleSql()` so it returns the first executed DDL statement:

```java
String singleSql() {
    assertThat(sqlList).isNotEmpty();
    return sqlList.get(0);
}
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```bash
mvn -pl modules/ai-agent-spring-boot-starter -Dtest=AiManagedSkillConfigSchemaInitializerTest test
```

Expected: FAIL because create SQL has no `api_registry_id` and initializer does not execute upgrade SQL.

- [ ] **Step 3: Implement schema upgrade**

In `AiManagedSkillConfigSchemaInitializer.java`, add constants:

```java
private static final String ADD_API_REGISTRY_ID_SQL = """
        ALTER TABLE ai_agent_skill_config
        ADD COLUMN api_registry_id BIGINT NULL COMMENT '闂佺绻愰悿鍥ㄧ閸儲鍎嶉柛鎴炩攰I濠电偛顦崝宀勫船閻ｅ本鍋橀柍顐ｆ箚'
        """;
private static final String API_REGISTRY_COLUMN_EXISTS_SQL = """
        SELECT COUNT(1)
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'ai_agent_skill_config'
          AND column_name = 'api_registry_id'
        """;
```

Add the column to `CREATE_TABLE_SQL` after `timeout_millis`:

```sql
api_registry_id BIGINT NULL COMMENT '闂佺绻愰悿鍥ㄧ閸儲鍎嶉柛鎴炩攰I濠电偛顦崝宀勫船閻ｅ本鍋橀柍顐ｆ箚',
```

Update `initialize()`:

```java
public void initialize() {
    jdbcTemplate.execute(CREATE_TABLE_SQL);
    if (!apiRegistryColumnExists()) {
        jdbcTemplate.execute(ADD_API_REGISTRY_ID_SQL);
    }
}

private boolean apiRegistryColumnExists() {
    Integer count = jdbcTemplate.queryForObject(API_REGISTRY_COLUMN_EXISTS_SQL, Integer.class);
    return count != null && count > 0;
}
```

- [ ] **Step 4: Update test fake datasource**

Teach `RecordingDataSource` to respond to `executeQuery` for `information_schema.columns` with one count row. The proxy should return `1` when `apiRegistryColumnExists = true`, otherwise `0`.

Use this shape in the fake `Statement` invocation:

```java
if ("executeQuery".equals(method.getName())) {
    int count = apiRegistryColumnExists ? 1 : 0;
    return resultSetProxy(count);
}
```

The `ResultSet` proxy only needs `next()`, `getInt(int)`, `getInt(String)`, and `close()`.

- [ ] **Step 5: Run schema tests**

Run:

```bash
mvn -pl modules/ai-agent-spring-boot-starter -Dtest=AiManagedSkillConfigSchemaInitializerTest test
```

Expected: PASS.

- [ ] **Step 6: Commit task changes**

```bash
git add modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiManagedSkillConfigSchemaInitializer.java modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/management/AiManagedSkillConfigSchemaInitializerTest.java
git commit -m "feat(ai): upgrade skill config schema for api registry id"
```

---

### Task 3: JDBC 婵炲濮甸幐鎼佸磻瀹ュ绠板ù锝夘棑閻ｄ粙鏌?`apiRegistryId`

**Files:**
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/JdbcAiManagedSkillConfigRepository.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/management/JdbcAiManagedSkillConfigRepositoryTest.java`

**Interfaces:**
- Consumes: `AiManagedSkillConfig#getApiRegistryId()` and `setApiRegistryId(Long)`.
- Produces: repository `save()` / `listActive()` / `findActiveByName()` preserve `api_registry_id`.

- [ ] **Step 1: Write failing repository assertions**

In `saveListUpdateFindAndSoftDeleteSkillConfig()`, set before first save:

```java
config.setApiRegistryId(1001L);
```

Assert after find:

```java
assertThat(item.getApiRegistryId()).isEqualTo(1001L);
```

Before update, clear it:

```java
saved.setApiRegistryId(null);
```

Assert after update:

```java
assertThat(item.getApiRegistryId()).isNull();
```

- [ ] **Step 2: Run test to verify failure**

Run:

```bash
mvn -pl modules/ai-agent-spring-boot-starter -Dtest=JdbcAiManagedSkillConfigRepositoryTest#saveListUpdateFindAndSoftDeleteSkillConfig test
```

Expected: FAIL because row mapper cannot read or repository cannot store `api_registry_id`.

- [ ] **Step 3: Update repository SQL and mapper**

In `SELECT_COLUMNS`, insert `api_registry_id` after `timeout_millis`:

```java
base_url, api_path, http_method, request_headers, timeout_millis, api_registry_id,
prompt_template_id, deleted, created_by, updated_by, created_at, updated_at
```

In `ROW_MAPPER`, add:

```java
config.setApiRegistryId(readNullableLong(rs.getObject("api_registry_id")));
```

In INSERT fields, insert `api_registry_id` after `timeout_millis`:

```sql
base_url, api_path, http_method, request_headers, timeout_millis, api_registry_id,
prompt_template_id, created_by, updated_by, created_at, updated_at
```

Increase INSERT placeholders from 16 to 17.

In UPDATE set list, add:

```sql
api_registry_id = ?,
```

between `timeout_millis = ?` and `prompt_template_id = ?`.

In `bindEditableFields`, add:

```java
ps.setObject(12, config.getApiRegistryId());
ps.setObject(13, config.getPromptTemplateId());
ps.setString(14, config.getCreatedBy());
ps.setString(15, config.getUpdatedBy());
```

Then update the timestamp/id indices:

```java
// insert
ps.setObject(16, toTimestamp(config.getCreatedAt()));
ps.setObject(17, toTimestamp(config.getUpdatedAt()));

// update
ps.setObject(16, toTimestamp(config.getUpdatedAt()));
ps.setObject(17, config.getId());
```

- [ ] **Step 4: Update fake datasource column mapping**

In `JdbcAiManagedSkillConfigRepositoryTest.FakeSkillConfigDataSource`, update `writeEditableFields()`:

```java
row.put("timeout_millis", params.get(offset + 10));
row.put("api_registry_id", params.get(offset + 11));
row.put("prompt_template_id", params.get(offset + 12));
row.put("created_by", params.get(offset + 13));
row.put("updated_by", params.get(offset + 14));
```

Update generated SELECT columns:

```java
"timeout_millis", "api_registry_id", "prompt_template_id", "deleted",
```

Update `newRow()`:

```java
row.put("created_at", params.get(15));
row.put("updated_at", params.get(16));
```

Update `updateRow()`:

```java
Long id = ((Number) params.get(16)).longValue();
row.put("updated_at", params.get(15));
```

- [ ] **Step 5: Run repository tests**

Run:

```bash
mvn -pl modules/ai-agent-spring-boot-starter -Dtest=JdbcAiManagedSkillConfigRepositoryTest test
```

Expected: PASS.

- [ ] **Step 6: Commit task changes**

```bash
git add modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/JdbcAiManagedSkillConfigRepository.java modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/management/JdbcAiManagedSkillConfigRepositoryTest.java
git commit -m "feat(ai): persist skill api registry id"
```

---

### Task 4: 闂佸搫鐗嗙粔瀛樻叏閻旇桨娌柛灞剧懅缁犱粙鎮楀☉娅偐绮畝鍕倞闁绘劕鐡ㄩ埢?
**Files:**
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiAgentManagementService.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/management/AiAgentManagementServiceTest.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/management/AiAgentManagementControllerTest.java`

**Interfaces:**
- Consumes:
  - `AiSkillApiConfigRequest#getApiRegistryId()`
  - `AiManagedSkillConfig#setApiRegistryId(Long)`
- Produces:
  - list/create/update responses include `apiConfig.apiRegistryId`
  - `updateApiSkillConfig(String, AiSkillApiConfigRequest)` saves `apiRegistryId`

- [ ] **Step 1: Write failing service tests**

Add to `AiAgentManagementServiceTest.java`:

```java
@Test
void persistsApiRegistryIdWhenCreatingAndUpdatingApiSkill() {
    AiSkillRegistry registry = new AiSkillRegistry(List.of());
    CapturingSkillConfigRepository repository = new CapturingSkillConfigRepository();
    AiAgentManagementService service = new AiAgentManagementService(
            registry, List.of(), repository, null);

    AiManagedSkillRequest request = apiSkillRequest("remote_quality_check", 1001L);
    AiManagedSkill created = service.createApiSkill(request);

    assertThat(created.apiConfig().apiRegistryId()).isEqualTo(1001L);
    assertThat(repository.findActiveByName("remote_quality_check"))
            .hasValueSatisfying(config -> assertThat(config.getApiRegistryId()).isEqualTo(1001L));

    AiManagedSkillRequest updateRequest = apiSkillRequest("remote_quality_check", null);
    AiManagedSkill updated = service.updateApiSkill("remote_quality_check", updateRequest);

    assertThat(updated.apiConfig().apiRegistryId()).isNull();
    assertThat(repository.findActiveByName("remote_quality_check"))
            .hasValueSatisfying(config -> assertThat(config.getApiRegistryId()).isNull());
}

@Test
void updatesOnlyApiConfigCanPersistApiRegistryId() {
    AiSkillRegistry registry = new AiSkillRegistry(List.of());
    CapturingSkillConfigRepository repository = new CapturingSkillConfigRepository();
    AiAgentManagementService service = new AiAgentManagementService(
            registry, List.of(), repository, null);
    service.createApiSkill(apiSkillRequest("remote_stock_query", null));

    AiSkillApiConfigRequest apiConfig = apiConfigRequest(2002L);
    apiConfig.setPath("/stock/current");
    AiManagedSkill updated = service.updateApiSkillConfig("remote_stock_query", apiConfig);

    assertThat(updated.apiConfig().apiRegistryId()).isEqualTo(2002L);
    assertThat(repository.findActiveByName("remote_stock_query"))
            .hasValueSatisfying(config -> assertThat(config.getApiRegistryId()).isEqualTo(2002L));
}
```

Add helpers in the same test class:

```java
private static AiManagedSkillRequest apiSkillRequest(String name, Long apiRegistryId) {
    AiManagedSkillRequest request = new AiManagedSkillRequest();
    request.setName(name);
    request.setDescription("remote api skill");
    request.setReadOnly(true);
    request.setApiConfig(apiConfigRequest(apiRegistryId));
    return request;
}

private static AiSkillApiConfigRequest apiConfigRequest(Long apiRegistryId) {
    AiSkillApiConfigRequest apiConfig = new AiSkillApiConfigRequest();
    apiConfig.setApiRegistryId(apiRegistryId);
    apiConfig.setEnabled(true);
    apiConfig.setBaseUrl("https://api.example.com");
    apiConfig.setPath("/quality");
    apiConfig.setMethod("POST");
    apiConfig.setHeaders(Map.of());
    apiConfig.setTimeoutMillis(3000);
    return apiConfig;
}

private static final class CapturingSkillConfigRepository extends EmptySkillConfigRepository {
    private final Map<String, AiManagedSkillConfig> saved = new LinkedHashMap<>();

    @Override
    public List<AiManagedSkillConfig> listActive() {
        return new ArrayList<>(saved.values());
    }

    @Override
    public Optional<AiManagedSkillConfig> findActiveByName(String skillName) {
        return Optional.ofNullable(saved.get(skillName));
    }

    @Override
    public AiManagedSkillConfig save(AiManagedSkillConfig config) {
        if (config.getId() == null) {
            config.setId((long) saved.size() + 1);
        }
        AiManagedSkillConfig copy = copyOf(config);
        saved.put(copy.getSkillName(), copy);
        return copy;
    }

    private AiManagedSkillConfig copyOf(AiManagedSkillConfig source) {
        AiManagedSkillConfig copy = new AiManagedSkillConfig();
        copy.setId(source.getId());
        copy.setAgentId(source.getAgentId());
        copy.setSkillName(source.getSkillName());
        copy.setSkillDescription(source.getSkillDescription());
        copy.setSkillType(source.getSkillType());
        copy.setReadOnly(source.isReadOnly());
        copy.setEnabled(source.isEnabled());
        copy.setBaseUrl(source.getBaseUrl());
        copy.setApiPath(source.getApiPath());
        copy.setHttpMethod(source.getHttpMethod());
        copy.setRequestHeaders(source.getRequestHeaders());
        copy.setTimeoutMillis(source.getTimeoutMillis());
        copy.setApiRegistryId(source.getApiRegistryId());
        copy.setPromptTemplateId(source.getPromptTemplateId());
        copy.setDeleted(source.isDeleted());
        return copy;
    }
}
```

- [ ] **Step 2: Run service tests to verify failure**

Run:

```bash
mvn -pl modules/ai-agent-spring-boot-starter -Dtest=AiAgentManagementServiceTest test
```

Expected: FAIL because service still returns/stores `null`.

- [ ] **Step 3: Implement service persistence**

In `saveSkillConfig()`, after timeout:

```java
config.setApiRegistryId(request.getApiConfig().getApiRegistryId());
```

Change `updateStoredApiConfig(AiApiSkillConfig saved)` signature:

```java
private void updateStoredApiConfig(AiApiSkillConfig saved, Long apiRegistryId)
```

Call it from `updateApiSkillConfig()`:

```java
updateStoredApiConfig(saved, request.getApiRegistryId());
```

Inside `updateStoredApiConfig`, add:

```java
config.setApiRegistryId(apiRegistryId);
```

Update `apiConfig(String skillName)` to read stored ID:

```java
private AiSkillApiConfigResponse apiConfig(String skillName) {
    Long apiRegistryId = findStoredSkill(skillName)
            .map(AiManagedSkillConfig::getApiRegistryId)
            .orElse(null);
    return skillRegistry.apiSkillConfig(skillName)
            .map(config -> new AiSkillApiConfigResponse(
                    config.enabled(),
                    config.baseUrl(),
                    config.path(),
                    config.method(),
                    redactedHeaders(config.headers()),
                    config.timeoutMillis(),
                    apiRegistryId))
            .orElse(null);
}
```

- [ ] **Step 4: Extend controller test**

In `AiAgentManagementControllerTest#createsUpdatesAndDeletesManagedApiSkill`, add to update payload:

```json
"apiRegistryId": null,
```

Add update assertion:

```java
.andExpect(jsonPath("$.apiConfig.apiRegistryId").isEmpty())
```

- [ ] **Step 5: Run service and controller tests**

Run:

```bash
mvn -pl modules/ai-agent-spring-boot-starter -Dtest=AiAgentManagementServiceTest,AiAgentManagementControllerTest test
```

Expected: PASS.

- [ ] **Step 6: Commit task changes**

```bash
git add modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiAgentManagementService.java modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/management/AiAgentManagementServiceTest.java modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/management/AiAgentManagementControllerTest.java
git commit -m "feat(ai): return skill api registry association"
```

---

### Task 5: 闂佸憡鎸哥粔鍫曨敂椤掑嫭鐒诲璺侯儏椤忋儱鈽夐幘鍐差劉缂佹棁宕电划姘偊濞嗙偓鏂€婵?
**Files:**
- Modify: `frontend/modules/ai/src/utils/api-registry.js`
- Modify: `frontend/modules/ai/src/utils/api-registry-search.js`
- Modify: `frontend/modules/ai/src/components/AiApiRegistrySelect.vue`
- Modify: `frontend/modules/ai/src/views/AiAgentManage.vue`
- Modify: `frontend/modules/ai/tests/api-registry-selection.test.mjs`

**Interfaces:**
- Consumes: backend `apiConfig.apiRegistryId`.
- Produces:
  - `mergeRegistryApiConfig(apiConfig, api)` returns `apiRegistryId`, `path`, `method`.
  - `AiApiRegistrySelect` supports `modelValue`, emits `update:modelValue`, `select`, `clear`.
  - `AiAgentManage` submits `skillForm.apiConfig.apiRegistryId`.

- [ ] **Step 1: Write failing utility tests**

In `api-registry-selection.test.mjs`, after the `merged` assertions, add:

```js
assert.equal(merged.apiRegistryId, null)

const linkedMerged = mergeRegistryApiConfig(original, {
  id: 1001,
  path: '/api/biz/project/list',
  method: 'get'
})
assert.equal(linkedMerged.apiRegistryId, 1001)
```

Add search controller clear test:

```js
const clearedIds = []
const clearController = createApiRegistrySearchController({
  query: () => Promise.resolve([]),
  onClear: id => clearedIds.push(id)
})
clearController.select(null, [{ id: 7 }])
assert.deepEqual(clearedIds, [null])
```

- [ ] **Step 2: Run frontend test to verify failure**

Run:

```bash
node --test frontend/modules/ai/tests/api-registry-selection.test.mjs
```

Expected: FAIL because `apiRegistryId` and `onClear` are not implemented.

- [ ] **Step 3: Implement utility behavior**

In `frontend/modules/ai/src/utils/api-registry.js`, update `mergeRegistryApiConfig`:

```js
export function mergeRegistryApiConfig(apiConfig = {}, api = {}) {
  const method = String(api.method || '').trim().toUpperCase()
  const id = Number.isFinite(Number(api.id)) ? Number(api.id) : null
  return {
    ...apiConfig,
    apiRegistryId: id,
    path: String(api.path || '').trim(),
    method: method || 'GET'
  }
}
```

In `frontend/modules/ai/src/utils/api-registry-search.js`, extend parameters:

```js
onSelect = () => {},
onClear = () => {}
```

Update `select`:

```js
function select(id, options = []) {
  if (disposed) return
  if (id == null) {
    onClear(null)
    return
  }
  const selected = options.find(option => option.id === id)
  if (selected) onSelect(selected)
}
```

- [ ] **Step 4: Update component contract**

In `AiApiRegistrySelect.vue`, add props and emits:

```js
const props = defineProps({
  modelValue: {
    type: [Number, String],
    default: null
  },
  fallbackPath: {
    type: String,
    default: ''
  },
  fallbackMethod: {
    type: String,
    default: ''
  }
})
const emit = defineEmits(['update:modelValue', 'select', 'clear'])
```

Replace `const selectedId = ref(null)` with:

```js
const selectedId = ref(props.modelValue)
```

Add watcher:

```js
watch(() => props.modelValue, value => {
  selectedId.value = value
})
```

Add `watch` import from Vue.

Add `onClear` to controller:

```js
onClear: () => {
  emit('update:modelValue', null)
  emit('clear')
}
```

Update `handleChange`:

```js
function handleChange(id) {
  selectedId.value = id
  emit('update:modelValue', id ?? null)
  searchController.select(id, options.value)
}
```

Add fallback option in template before normal options:

```vue
<el-option
  v-if="selectedId && !options.some(api => api.id === selectedId)"
  :label="`閻庣懓鎲¤ぐ鍐矗瑜旈幊鏇㈠籍閳ь剛鏁幘顔肩煑?#${selectedId}`"
  :value="selectedId"
>
  <div class="api-option">
    <span class="api-option__module">閻庣懓鎲¤ぐ鍐矗瑜旈幊鏇㈠籍閳ь剛鏁幘顔肩煑?#{{ selectedId }}</span>
    <span class="api-option__method">{{ fallbackMethod || '-' }}</span>
    <span class="api-option__path">{{ fallbackPath || '-' }}</span>
  </div>
</el-option>
```

- [ ] **Step 5: Update page form**

In `defaultSkillForm().apiConfig`, add:

```js
apiRegistryId: null,
```

In the picker markup, use:

```vue
<AiApiRegistrySelect
  :key="skillRegistryPickerKey"
  v-model="skillForm.apiConfig.apiRegistryId"
  :fallback-path="skillForm.apiConfig.path"
  :fallback-method="skillForm.apiConfig.method"
  @select="applyRegisteredApi"
/>
```

In `openEditSkill()`, add:

```js
apiRegistryId: skill.apiConfig?.apiRegistryId || null,
```

The existing save payload spreads `skillForm.apiConfig`; keep that shape so `apiRegistryId` is sent to backend.

- [ ] **Step 6: Extend static tests**

In `api-registry-selection.test.mjs`, add assertions:

```js
assert.match(pickerSource, /defineProps/, '闂備緡鍋勯ˇ鎵偓姘ュ妿缁辨帡宕熼鍜佸仺闁圭厧鐡ㄥ鍦暜閹绢喖缁?modelValue 婵炴垶鎸告鎼佸储鐟欏嫭鍎熼柡鍥╁仦缁€鈧梺鍝勫閸庢娊鎮鸿閳?)
assert.match(pickerSource, /update:modelValue/, '闂備緡鍋勯ˇ鎵偓姘ュ妿缁辨帡宕熼鍜佸仺闁圭厧鐡ㄥ褰掑极椤曗偓楠?v-model 闂佹悶鍎抽崑娑㈠疮閹捐绀傞悗鍦С缁?ID')
assert.match(pickerSource, /emit\('clear'\)/, '濠电偞鎸搁幊鎰板煘閺嶎厽鐒诲璺侯儏椤忋儵鏌￠崘顓熺【缂併劍鐓″畷锝夊箣濠靛棙鐦?clear 婵炲瓨绮岄鍕枎?)
assert.match(pickerSource, /閻庣懓鎲¤ぐ鍐矗瑜旈幊鏇㈠籍閳ь剛鏁幘顔肩煑?#/, '缂傚倸鍊归悧鐐垫椤愶箑绠戝ù锝呮惈缁讳線鎮樿箛鏂跨仸闁轰胶鍋ゅ畷妯虹暆閸愶腹鍋撻崘顏呭闁哄娉曠粔鍧楁煛閸愵厽纭剧紒銊︾叀瀵即宕滆娴犳盯鏌涜箛鏃傜煀缂併劍濞婂畷妤冣偓鍦С缁挸菐閸ワ絽澧插ù?)
assert.match(registryPickerMarkup, /v-model="skillForm\.apiConfig\.apiRegistryId"/, '婵＄偑鍊楅弫璇差焽閻楀牊鍎熼柡鍐ｅ亾濠碘剝鎮傞弻鍛緞鐎ｎ亶浠撮梺闈╃祷閸斿海鍒掗敂閿亾鐟欏嫯澹橀柛?apiRegistryId')
assert.match(agentPage, /apiRegistryId:\s*null/, '婵帗绋掗…鍫ヮ敇婵犳艾绠柍褜鍓熼幊妤呭磹閻旂补鍋撻崘顔肩闁哄洨鍋熺€瑰鏌涢幒鎾剁畵妞ゎ偅鍔欏畷?apiRegistryId')
assert.match(editSkillBlock, /apiRegistryId:\s*skill\.apiConfig\?\.apiRegistryId\s*\|\|\s*null/, '缂傚倸鍊归悧鐐垫椤愶箑绠柍褜鍓熼幊妤呮嚍閵夛富妲柟鐓庣摠閺屻劌煤閺嶃劎绠?apiRegistryId')
```

- [ ] **Step 7: Run frontend tests**

Run:

```bash
node --test frontend/modules/ai/tests/api-registry-selection.test.mjs
```

Expected: PASS.

- [ ] **Step 8: Commit task changes**

```bash
git add frontend/modules/ai/src/utils/api-registry.js frontend/modules/ai/src/utils/api-registry-search.js frontend/modules/ai/src/components/AiApiRegistrySelect.vue frontend/modules/ai/src/views/AiAgentManage.vue frontend/modules/ai/tests/api-registry-selection.test.mjs
git commit -m "feat(ai-ui): persist selected api registry id"
```

---

### Task 6: 闂佺绻堥崝鎴﹀闯閻戞﹩娈界€光偓閸愵亝顫嶆繛鎴炴尭妤犵煤閺嶎兙浜归柟鐑樻惄濮婇箖鏌?
**Files:**
- Verify only unless tests reveal a defect.

**Interfaces:**
- Consumes: all tasks above.
- Produces: verified backend starter tests and frontend build.

- [ ] **Step 1: Run focused backend tests**

Run:

```bash
mvn -pl modules/ai-agent-spring-boot-starter -Dtest=AiManagedSkillConfigSchemaInitializerTest,JdbcAiManagedSkillConfigRepositoryTest,AiAgentManagementServiceTest,AiAgentManagementControllerTest test
```

Expected: PASS.

- [ ] **Step 2: Run focused frontend tests**

Run:

```bash
node --test frontend/modules/ai/tests/api-registry-selection.test.mjs frontend/modules/ai/tests/current-user-static.test.mjs frontend/modules/ai/tests/prompt-template-static.test.mjs
```

Expected: PASS.

- [ ] **Step 3: Run frontend build**

Run from `frontend/web-shell`:

```bash
npm run build
```

Expected: PASS. Existing Vite chunk-size warnings are acceptable if no new build error appears.

- [ ] **Step 4: Run starter package check**

Run:

```bash
mvn -pl modules/ai-agent-spring-boot-starter -am package
```

Expected: PASS.

- [ ] **Step 5: Inspect changed files**

Run:

```bash
git diff --stat
git diff -- modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management frontend/modules/ai/src
```

Expected: diff only contains API 濠电偛顦崝宀勫船娴犲绾ч柕澶涘閻?ID 闂佸綊鏅插鎺旂不濞嗘挸绀岄柡宥庡亝缁佹煡鏌涜箛鎾虫毐鐟滄澘顦靛瀛樻綇閸撗咁槷婵炴垶鎸哥粔瀵糕偓鍨耿瀹曘儵顢曢妶搴濈磽闂佸憡鐟辩徊濠氬焵椤戣法鍔嶆俊顐犲€濆畷妤呮惞椤愩倕顥庨悗娈垮枛缁绘垿宕归妸鈺傚仺闁绘梻顭堥悘鍥瑰┃鐘叉噹閳锋牠鏌?
- [ ] **Step 6: Final commit**

If previous task commits were not made, commit all task files together:

```bash
git add docs/superpowers/specs/2026-07-22-ai-skill-api-registry-persistence-design.md docs/superpowers/plans/2026-07-22-ai-skill-api-registry-persistence.md modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiSkillApiConfigRequest.java modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiSkillApiConfigResponse.java modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiManagedSkillConfig.java modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiManagedSkillConfigSchemaInitializer.java modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/JdbcAiManagedSkillConfigRepository.java modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiAgentManagementService.java modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/management/AiManagedSkillConfigSchemaInitializerTest.java modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/management/JdbcAiManagedSkillConfigRepositoryTest.java modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/management/AiAgentManagementServiceTest.java modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/management/AiAgentManagementControllerTest.java frontend/modules/ai/src/utils/api-registry.js frontend/modules/ai/src/utils/api-registry-search.js frontend/modules/ai/src/components/AiApiRegistrySelect.vue frontend/modules/ai/src/views/AiAgentManage.vue frontend/modules/ai/tests/api-registry-selection.test.mjs
git commit -m "feat(ai): persist skill api registry association"
```

---

## Self-Review

- Spec coverage: 数据库新增字段、兼容升级、后端 DTO/实体/仓储/服务、前端选择/清空/回显、测试策略均已对应到任务。
- Placeholder scan: 未发现占位内容。
- Type consistency: 后端统一使用 `apiRegistryId` Java 属性与 `api_registry_id` MySQL 字段；前端统一使用 `apiConfig.apiRegistryId`。
- Scope check: 本计划只实现技能 API 来源 ID 持久化，不实现 API 差异比对和注册表强校验。