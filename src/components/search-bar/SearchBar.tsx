interface Props {
  handleInput: (text: string) => void;
}

function SearchBar({ handleInput }: Props) {
  return (
    <div className='search-bar-container'>
      <input
        type='text'
        placeholder='Започни да въвеждаш...'
        onInput={(e) => handleInput(e.currentTarget.value)}
      ></input>
      <svg
        width='33'
        height='44'
        viewBox='0 0 33 44'
        fill='none'
        xmlns='http://www.w3.org/2000/svg'
      >
        <path
          d='M23.5588 13.023C23.5537 18.7274 18.9254 23.3516 13.2212 23.3516C7.51701 23.3516 2.89687 18.7274 2.90192 13.023C2.90696 7.31858 7.53529 2.69434 13.2395 2.69434C18.9437 2.69434 23.5638 7.31858 23.5588 13.023Z'
          stroke='#5B5B5B'
          strokeWidth='5'
        />
        <rect
          width='4.2766'
          height='24.0775'
          rx='2.1383'
          transform='matrix(0.866019 -0.500011 0.499266 0.866449 16.6988 22.7252)'
          fill='#5B5B5B'
        />
      </svg>
    </div>
  );
}

export default SearchBar;
