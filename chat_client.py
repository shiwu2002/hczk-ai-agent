"""
桓宸智科AI平台 - 命令行聊天客户端
支持流式/非流式对话，兼容纯文本和OpenAI JSON两种SSE格式
"""

import requests
import json
import sys

# ==================== 配置 ====================
BASE_URL = "http://localhost:8080/api"
API_KEY = "sk-hczk-tzoa4eb5t9hwuiwlf7r8d6sf"
# 调用方式：None=自动选择模型，"model-{id}"=指定模型，"agent-{id}"=指定智能体，模型名称如"qwen-plus"
MODEL = None
STREAM = True
# =============================================

HEADERS = {
    "Authorization": f"Bearer {API_KEY}",
    "Content-Type": "application/json",
}


def build_payload(messages: list, stream: bool = True) -> dict:
    """构建请求体，根据 MODEL 配置自动决定调用方式"""
    payload = {"messages": messages, "stream": stream}
    if MODEL:
        payload["model"] = MODEL
    return payload


def parse_sse_line(line: str) -> str | None:
    """
    解析 SSE 数据行，兼容两种格式：
    - OpenAI JSON: data: {"choices":[{"delta":{"content":"..."}}]}
    - 纯文本: data: 你好
    """
    if not line.startswith("data:"):
        return None

    data_str = line[5:].strip()  # 去掉 "data:" 前缀及空格

    if data_str == "[DONE]":
        return None

    # 尝试 JSON 格式解析（OpenAI 兼容）
    try:
        chunk = json.loads(data_str)
        return chunk.get("choices", [{}])[0].get("delta", {}).get("content", "")
    except (json.JSONDecodeError, IndexError, KeyError):
        pass

    # JSON 解析失败，当作纯文本
    return data_str


def chat_stream(messages: list) -> str:
    """流式请求，逐块打印并返回完整回复"""
    payload = build_payload(messages, stream=True)
    resp = requests.post(f"{BASE_URL}/v1/chat/completions", headers=HEADERS, json=payload, stream=True)
    resp.raise_for_status()

    full_response = ""
    for line in resp.iter_lines():
        if not line:
            continue
        decoded = line.decode("utf-8")
        content = parse_sse_line(decoded)
        if content is None:
            continue
        print(content, end="", flush=True)
        full_response += content

    return full_response


def chat_non_stream(messages: list) -> str:
    """非流式请求，一次性返回完整回复"""
    payload = build_payload(messages, stream=False)
    resp = requests.post(f"{BASE_URL}/v1/chat/completions", headers=HEADERS, json=payload)
    resp.raise_for_status()

    # 非流式时整个响应体可能是纯文本或 JSON
    text = resp.text
    try:
        data = json.loads(text)
        return data.get("choices", [{}])[0].get("message", {}).get("content", text)
    except (json.JSONDecodeError, IndexError, KeyError):
        return text


def main():
    messages = []
    model_hint = MODEL if MODEL else "自动选择"

    print(f"AI 聊天机器人已启动 (模型: {model_hint}, 流式: {STREAM})")
    print("输入 'exit' 或 'quit' 退出，输入 'clear' 清空对话历史")
    print("-" * 50)

    while True:
        try:
            user_input = input("\n你: ").strip()
        except (EOFError, KeyboardInterrupt):
            print("\n再见！")
            break

        if not user_input:
            continue
        if user_input.lower() in ("exit", "quit"):
            print("再见！")
            break
        if user_input.lower() == "clear":
            messages.clear()
            print("对话历史已清空")
            continue

        messages.append({"role": "user", "content": user_input})

        try:
            print("AI: ", end="", flush=True)
            if STREAM:
                reply = chat_stream(messages)
            else:
                reply = chat_non_stream(messages)
                print(reply, end="", flush=True)
            print("\n" + "-" * 50)

            if reply:
                messages.append({"role": "assistant", "content": reply})
        except requests.exceptions.ConnectionError:
            print("\n[错误] 无法连接服务器，请检查服务是否启动")
        except requests.exceptions.HTTPError as e:
            print(f"\n[错误] HTTP {e.response.status_code}: {e.response.text[:200]}")
        except requests.exceptions.RequestException as e:
            print(f"\n[错误] 请求失败: {e}")


if __name__ == "__main__":
    main()
