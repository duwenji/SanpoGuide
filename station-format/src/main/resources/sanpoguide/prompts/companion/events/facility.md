{{!
  散歩の友: 状況に合った施設が近くにあるとき（施設ごとに1回の散歩で1度だけ。判定は FacilityAdvisor）。
  変数:
    label          施設の種類（トイレ・水飲み場・自動販売機・休憩所・ベンチ）
    name           施設の名前。なければ null
    distance_m     施設までの距離（10m 単位）
    direction      歩いている向きから見た方向（前方・右手・左手・後ろ）。立ち止まっているときは null
    need_shelter   雨・雪で、屋根のある休憩所を案内するとき true
    rain_coming    need_shelter のうち、まだ降っておらず、1時間以内に降る予報のとき true
    need_toilet    しばらく歩いたので、トイレを案内するとき true
    need_drink     暑いので、水分補給の場所を案内するとき true
    need_seat      長く歩いたので、座れる場所を案内するとき true
}}
# 出来事
{{#direction}}{{direction}}{{/direction}}{{distance_m}}mほどのところに{{label}}{{#name}}（{{name}}）{{/name}}があります。
{{#need_shelter}}
{{^rain_coming}}
雨や雪が降っているので、雨宿りできる場所として伝えてください。
{{/rain_coming}}
{{#rain_coming}}
まだ降っていませんが、このあと雨や雪になる予報なので、降ってきたときに雨宿りできる場所として伝えてください。
{{/rain_coming}}
{{/need_shelter}}
{{#need_toilet}}
しばらく歩いているので、念のためトイレの場所として伝えてください。行くよう勧めたり、体調を詮索したりしないでください。
{{/need_toilet}}
{{#need_drink}}
気温が高いので、水分補給できる場所として伝えてください。
{{/need_drink}}
{{#need_seat}}
長く歩いているので、ひと休みできる場所として伝えてください。
{{/need_seat}}
場所と距離がはっきり伝わるように、短く一言で話してください。見えていないもの（看板や景色など）は話さないでください。
